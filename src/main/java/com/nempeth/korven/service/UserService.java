package com.nempeth.korven.service;

import com.nempeth.korven.constants.MembershipRole;
import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.persistence.entity.BusinessMembership;
import com.nempeth.korven.persistence.entity.User;
import com.nempeth.korven.persistence.repository.BusinessMembershipRepository;
import com.nempeth.korven.persistence.repository.BusinessRepository;
import com.nempeth.korven.persistence.repository.ReservationRepository;
import com.nempeth.korven.persistence.repository.SaleRepository;
import com.nempeth.korven.persistence.repository.UserRepository;
import com.nempeth.korven.rest.dto.BusinessMembershipResponse;
import com.nempeth.korven.rest.dto.UpdateMembershipRoleRequest;
import com.nempeth.korven.rest.dto.UpdateMembershipStatusRequest;
import com.nempeth.korven.rest.dto.UpdateUserProfileRequest;
import com.nempeth.korven.rest.dto.UpdateUserPasswordRequest;
import com.nempeth.korven.rest.dto.UserResponse;
import com.nempeth.korven.utils.PasswordUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final BusinessRepository businessRepository;
    private final SaleRepository saleRepository;
    private final ReservationRepository reservationRepository;
    private final EntityManager entityManager;

    private static final Pattern EMAIL_RX =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);


    @Transactional
    public boolean updateUserProfile(UUID userId, String requesterEmail, UpdateUserProfileRequest req) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!target.getEmail().equalsIgnoreCase(requesterEmail)) {
            throw new AccessDeniedException("No autorizado para modificar este usuario");
        }
        boolean emailChanged = false;

        if (req.email() != null && !req.email().isBlank()
                && !req.email().equalsIgnoreCase(target.getEmail())) {

            if (!EMAIL_RX.matcher(req.email()).matches()) {
                throw new IllegalArgumentException("Email con formato inválido");
            }

            userRepository.findByEmailIgnoreCase(req.email()).ifPresent(existing -> {
                if (!existing.getId().equals(target.getId())) {
                    throw new IllegalArgumentException("Email ya registrado");
                }
            });

            target.setEmail(req.email());
            emailChanged = true;
        }

        if (req.name() != null)     target.setName(req.name());
        if (req.lastName() != null) target.setLastName(req.lastName());
        userRepository.save(target);
        return emailChanged;
    }

    @Transactional
    public void updateUserPassword(UUID userId, String requesterEmail, UpdateUserPasswordRequest req) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!target.getEmail().equalsIgnoreCase(requesterEmail)) {
            throw new AccessDeniedException("No autorizado para modificar este usuario");
        }
        if (req.currentPassword() == null || req.currentPassword().isBlank()) {
            throw new IllegalArgumentException("La contraseña actual es requerida");
        }
        if (!PasswordUtils.matches(req.currentPassword(), target.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            target.setPasswordHash(PasswordUtils.hash(req.newPassword()));
            userRepository.save(target);
        } else {
            throw new IllegalArgumentException("La nueva contraseña no puede estar vacía");
        }
    }

    @Transactional
    public void deleteUser(UUID userId, String requesterEmail) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (!target.getEmail().equalsIgnoreCase(requesterEmail)) {
            throw new AccessDeniedException("No autorizado para borrar este usuario");
        }

        List<BusinessMembership> memberships = membershipRepository.findByUserId(userId);

        // Buscar si el usuario es OWNER de algún negocio
        List<BusinessMembership> ownerMemberships = memberships.stream()
                .filter(m -> m.getRole() == MembershipRole.OWNER)
                .toList();

        if (!ownerMemberships.isEmpty()) {
            // --- Flujo OWNER: eliminar negocio completo + empleados ---
            for (BusinessMembership ownerMembership : ownerMemberships) {
                UUID businessId = ownerMembership.getBusiness().getId();

                // Eliminar los Users de todos los empleados del negocio
                List<BusinessMembership> employeeMemberships = membershipRepository.findByBusinessId(businessId)
                        .stream()
                        .filter(m -> !m.getUser().getId().equals(userId))
                        .toList();

                for (BusinessMembership empMembership : employeeMemberships) {
                    UUID empUserId = empMembership.getUser().getId();
                    // Nullificar FKs del empleado antes de eliminarlo
                    saleRepository.nullifyCreatedByUser(empUserId);
                    reservationRepository.nullifyCreatedByUser(empUserId);
                    userRepository.delete(empMembership.getUser());
                }

                // Eliminar la membresía del OWNER antes de borrar el Business
                // (evita que Hibernate intente setear business_id=NULL en la membresía)
                membershipRepository.delete(ownerMembership);

                // Flush + clear: forzar sync con DB y limpiar contexto de Hibernate
                // para que no intente manejar entidades stale (sales, etc.)
                entityManager.flush();
                entityManager.clear();

                // Eliminar el Business (DB CASCADE elimina: categorías, productos, ventas,
                // sale_items, mesas, reservas, goals, goal_category_targets)
                businessRepository.deleteById(businessId);
            }

            // Re-obtener el usuario porque el clear() desasoció todas las entidades
            target = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        } else {
            // --- Flujo EMPLOYEE: verificar órdenes abiertas y limpiar refs ---
            if (saleRepository.existsByCreatedByUserIdAndOccurredAtIsNull(userId)) {
                throw new IllegalArgumentException(
                        "Debe cerrar sus órdenes abiertas antes de eliminar su usuario");
            }

            // Nullificar FK en ventas y reservas (el nombre queda en created_by_user_name)
            saleRepository.nullifyCreatedByUser(userId);
            reservationRepository.nullifyCreatedByUser(userId);
        }

        // Eliminar el usuario (DB CASCADE elimina: business_membership, password_reset_token)
        userRepository.delete(target);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        
        List<BusinessMembershipResponse> businesses = membershipRepository
                .findByUserIdAndStatus(user.getId(), MembershipStatus.ACTIVE)
                .stream()
                .map(this::mapToMembershipResponse)
                .toList();
        
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .lastName(user.getLastName())
                .businesses(businesses)
                .build();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId, String requesterEmail) {
        // Verificar que el usuario solicitante existe
        User requester = userRepository.findByEmailIgnoreCase(requesterEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario solicitante no encontrado"));

        // Obtener el usuario objetivo
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Verificar que el solicitante tiene acceso a ver este usuario
        // (debe ser el mismo usuario o tener acceso a través de un negocio compartido)
        if (!requester.getId().equals(userId)) {
            // Verificar si comparten al menos un negocio
            List<BusinessMembership> requesterMemberships = membershipRepository
                    .findByUserId(requester.getId());
            
            List<BusinessMembership> targetMemberships = membershipRepository
                    .findByUserId(userId);

            boolean hasSharedBusiness = requesterMemberships.stream()
                    .anyMatch(reqMembership -> targetMemberships.stream()
                            .anyMatch(targetMembership -> 
                                    reqMembership.getBusiness().getId().equals(targetMembership.getBusiness().getId())));

            if (!hasSharedBusiness) {
                throw new AccessDeniedException("No tienes acceso para ver los datos de este usuario");
            }
        }

        List<BusinessMembershipResponse> businesses = membershipRepository
                .findByUserId(targetUser.getId())
                .stream()
                .map(this::mapToMembershipResponse)
                .toList();
        
        return UserResponse.builder()
                .id(targetUser.getId())
                .email(targetUser.getEmail())
                .name(targetUser.getName())
                .lastName(targetUser.getLastName())
                .businesses(businesses)
                .build();
    }

    private BusinessMembershipResponse mapToMembershipResponse(BusinessMembership membership) {
        return BusinessMembershipResponse.builder()
                .businessId(membership.getBusiness().getId())
                .businessName(membership.getBusiness().getName())
                .role(membership.getRole())
                .status(membership.getStatus())
                .build();
    }

    @Transactional
    public void updateMembershipStatus(UUID businessId, UUID userId, String requesterEmail, UpdateMembershipStatusRequest req) {
        // Verificar que el usuario solicitante tiene acceso al negocio y permisos
        User requester = userRepository.findByEmailIgnoreCase(requesterEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario solicitante no encontrado"));

        BusinessMembership requesterMembership = membershipRepository.findByBusinessIdAndUserId(businessId, requester.getId())
                .orElseThrow(() -> new IllegalArgumentException("No tienes acceso a este negocio"));

        // Solo permitir que propietarios actualicen el status de membresía
        if (requesterMembership.getRole() != MembershipRole.OWNER) {
            throw new AccessDeniedException("Solo los propietarios pueden actualizar el status de membresía");
        }

        if (requesterMembership.getStatus() != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException("Tu membresía en este negocio no está activa");
        }

        // Encontrar la membresía del usuario objetivo
        BusinessMembership targetMembership = membershipRepository.findByBusinessIdAndUserId(businessId, userId)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no es miembro de este negocio"));

        // No permitir que el propietario cambie su propio status
        if (requester.getId().equals(userId)) {
            throw new IllegalArgumentException("No puedes cambiar tu propio status de membresía");
        }

        // Actualizar el status
        targetMembership.setStatus(req.status());
        membershipRepository.save(targetMembership);
    }

    @Transactional
    public void updateMembershipRole(UUID businessId, UUID userId, String requesterEmail, UpdateMembershipRoleRequest req) {
        // Verificar que el usuario solicitante tiene acceso al negocio y permisos
        User requester = userRepository.findByEmailIgnoreCase(requesterEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario solicitante no encontrado"));

        BusinessMembership requesterMembership = membershipRepository.findByBusinessIdAndUserId(businessId, requester.getId())
                .orElseThrow(() -> new IllegalArgumentException("No tienes acceso a este negocio"));

        // Solo permitir que propietarios actualicen el role de membresía
        if (requesterMembership.getRole() != MembershipRole.OWNER) {
            throw new AccessDeniedException("Solo los propietarios pueden actualizar el role de membresía");
        }

        if (requesterMembership.getStatus() != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException("Tu membresía en este negocio no está activa");
        }

        // Encontrar la membresía del usuario objetivo
        BusinessMembership targetMembership = membershipRepository.findByBusinessIdAndUserId(businessId, userId)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no es miembro de este negocio"));

        // No permitir que el propietario cambie su propio role
        if (requester.getId().equals(userId)) {
            throw new IllegalArgumentException("No puedes cambiar tu propio role de membresía");
        }

        // Validar que solo se pueda cambiar de EMPLOYEE a OWNER
        if (targetMembership.getRole() != MembershipRole.EMPLOYEE) {
            throw new IllegalArgumentException("Solo se puede promocionar empleados a propietarios");
        }

        if (req.role() != MembershipRole.OWNER) {
            throw new IllegalArgumentException("Solo se puede promocionar a propietario (OWNER)");
        }

        // Actualizar el role
        targetMembership.setRole(req.role());
        membershipRepository.save(targetMembership);
    }
}
