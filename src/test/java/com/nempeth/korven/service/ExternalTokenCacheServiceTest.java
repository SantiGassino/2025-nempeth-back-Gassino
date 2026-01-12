package com.nempeth.korven.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalTokenCacheService Tests")
class ExternalTokenCacheServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private ExternalTokenCacheService cacheService;

    private static final String CACHE_NAME = "externalTokens";
    private String testClientName;
    private String testToken;

    @BeforeEach
    void setUp() {
        testClientName = "TestClient";
        testToken = "testToken123";
    }

    @Test
    @DisplayName("Should store token in cache")
    void shouldStoreTokenInCache() {
        // Given
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        // When
        cacheService.storeToken(testClientName, testToken);

        // Then
        verify(cacheManager).getCache(CACHE_NAME);
        verify(cache).put(testClientName, testToken);
    }

    @Test
    @DisplayName("Should handle null cache when storing token")
    void shouldHandleNullCacheWhenStoringToken() {
        // Given
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(null);

        // When
        cacheService.storeToken(testClientName, testToken);

        // Then
        verify(cacheManager).getCache(CACHE_NAME);
        verify(cache, never()).put(anyString(), anyString());
    }

    @Test
    @DisplayName("Should retrieve active token from cache")
    void shouldRetrieveActiveTokenFromCache() {
        // Given
        Cache.ValueWrapper valueWrapper = mock(Cache.ValueWrapper.class);
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(testClientName)).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(testToken);

        // When
        String result = cacheService.getActiveToken(testClientName);

        // Then
        assertThat(result).isEqualTo(testToken);
        verify(cache).get(testClientName);
    }

    @Test
    @DisplayName("Should return null when token not found in cache")
    void shouldReturnNullWhenTokenNotFoundInCache() {
        // Given
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(testClientName)).thenReturn(null);

        // When
        String result = cacheService.getActiveToken(testClientName);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null when cache is null")
    void shouldReturnNullWhenCacheIsNull() {
        // Given
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(null);

        // When
        String result = cacheService.getActiveToken(testClientName);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should verify token is active when it matches cached token")
    void shouldVerifyTokenIsActiveWhenItMatchesCachedToken() {
        // Given
        Cache.ValueWrapper valueWrapper = mock(Cache.ValueWrapper.class);
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(testClientName)).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(testToken);

        // When
        boolean isActive = cacheService.isActiveToken(testClientName, testToken);

        // Then
        assertThat(isActive).isTrue();
    }

    @Test
    @DisplayName("Should verify token is not active when it does not match cached token")
    void shouldVerifyTokenIsNotActiveWhenItDoesNotMatchCachedToken() {
        // Given
        String differentToken = "differentToken456";
        Cache.ValueWrapper valueWrapper = mock(Cache.ValueWrapper.class);
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(testClientName)).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(testToken);

        // When
        boolean isActive = cacheService.isActiveToken(testClientName, differentToken);

        // Then
        assertThat(isActive).isFalse();
    }

    @Test
    @DisplayName("Should accept any token when no token in cache")
    void shouldAcceptAnyTokenWhenNoTokenInCache() {
        // Given
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(testClientName)).thenReturn(null);

        // When
        boolean isActive = cacheService.isActiveToken(testClientName, testToken);

        // Then
        assertThat(isActive).isTrue();
    }

    @Test
    @DisplayName("Should accept any token when cache is null")
    void shouldAcceptAnyTokenWhenCacheIsNull() {
        // Given
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(null);

        // When
        boolean isActive = cacheService.isActiveToken(testClientName, testToken);

        // Then
        assertThat(isActive).isTrue();
    }

    @Test
    @DisplayName("Should invalidate token for client")
    void shouldInvalidateTokenForClient() {
        // Given
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        // When
        cacheService.invalidateToken(testClientName);

        // Then
        verify(cache).evict(testClientName);
    }

    @Test
    @DisplayName("Should handle null cache when invalidating token")
    void shouldHandleNullCacheWhenInvalidatingToken() {
        // Given
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(null);

        // When
        cacheService.invalidateToken(testClientName);

        // Then
        verify(cache, never()).evict(anyString());
    }

    @Test
    @DisplayName("Should store and retrieve same token")
    void shouldStoreAndRetrieveSameToken() {
        // Given
        Cache.ValueWrapper valueWrapper = mock(Cache.ValueWrapper.class);
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(testClientName)).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(testToken);

        // When
        cacheService.storeToken(testClientName, testToken);
        String retrieved = cacheService.getActiveToken(testClientName);

        // Then
        assertThat(retrieved).isEqualTo(testToken);
    }

    @Test
    @DisplayName("Should handle multiple clients independently")
    void shouldHandleMultipleClientsIndependently() {
        // Given
        String client1 = "Client1";
        String client2 = "Client2";
        String token1 = "token1";
        String token2 = "token2";
        
        Cache.ValueWrapper wrapper1 = mock(Cache.ValueWrapper.class);
        Cache.ValueWrapper wrapper2 = mock(Cache.ValueWrapper.class);
        
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(client1)).thenReturn(wrapper1);
        when(cache.get(client2)).thenReturn(wrapper2);
        when(wrapper1.get()).thenReturn(token1);
        when(wrapper2.get()).thenReturn(token2);

        // When
        cacheService.storeToken(client1, token1);
        cacheService.storeToken(client2, token2);
        String retrievedToken1 = cacheService.getActiveToken(client1);
        String retrievedToken2 = cacheService.getActiveToken(client2);

        // Then
        assertThat(retrievedToken1).isEqualTo(token1);
        assertThat(retrievedToken2).isEqualTo(token2);
        verify(cache).put(client1, token1);
        verify(cache).put(client2, token2);
    }

    @Test
    @DisplayName("Should override old token with new token")
    void shouldOverrideOldTokenWithNewToken() {
        // Given
        String oldToken = "oldToken";
        String newToken = "newToken";
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        // When
        cacheService.storeToken(testClientName, oldToken);
        cacheService.storeToken(testClientName, newToken);

        // Then
        verify(cache).put(testClientName, oldToken);
        verify(cache).put(testClientName, newToken);
        verify(cache, times(2)).put(eq(testClientName), anyString());
    }

    @Test
    @DisplayName("Should handle client names with special characters")
    void shouldHandleClientNamesWithSpecialCharacters() {
        // Given
        String specialClientName = "Client@Name#123";
        Cache.ValueWrapper valueWrapper = mock(Cache.ValueWrapper.class);
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(specialClientName)).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(testToken);

        // When
        cacheService.storeToken(specialClientName, testToken);
        String retrieved = cacheService.getActiveToken(specialClientName);

        // Then
        assertThat(retrieved).isEqualTo(testToken);
        verify(cache).put(specialClientName, testToken);
    }

    @Test
    @DisplayName("Should handle very long tokens")
    void shouldHandleVeryLongTokens() {
        // Given
        String longToken = "token".repeat(1000);
        Cache.ValueWrapper valueWrapper = mock(Cache.ValueWrapper.class);
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(testClientName)).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(longToken);

        // When
        cacheService.storeToken(testClientName, longToken);
        String retrieved = cacheService.getActiveToken(testClientName);

        // Then
        assertThat(retrieved).isEqualTo(longToken);
    }

    @Test
    @DisplayName("Should verify exact token match")
    void shouldVerifyExactTokenMatch() {
        // Given
        String similarToken = testToken + "extra";
        Cache.ValueWrapper valueWrapper = mock(Cache.ValueWrapper.class);
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(testClientName)).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(testToken);

        // When
        boolean exactMatch = cacheService.isActiveToken(testClientName, testToken);
        boolean similarMatch = cacheService.isActiveToken(testClientName, similarToken);

        // Then
        assertThat(exactMatch).isTrue();
        assertThat(similarMatch).isFalse();
    }

    @Test
    @DisplayName("Should handle null value wrapper gracefully")
    void shouldHandleNullValueWrapperGracefully() {
        // Given
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(testClientName)).thenReturn(null);

        // When
        String result = cacheService.getActiveToken(testClientName);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should accept token after invalidation when no new token stored")
    void shouldAcceptTokenAfterInvalidationWhenNoNewTokenStored() {
        // Given
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
        when(cache.get(testClientName)).thenReturn(null);

        // When
        cacheService.invalidateToken(testClientName);
        boolean isActive = cacheService.isActiveToken(testClientName, "anyToken");

        // Then
        assertThat(isActive).isTrue(); // Should accept any token when cache is empty
    }
}
