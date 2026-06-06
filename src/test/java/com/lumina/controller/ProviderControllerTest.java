package com.lumina.controller;

import com.lumina.dto.ApiResponse;
import com.lumina.entity.Provider;
import com.lumina.entity.ProviderEndpoint;
import com.lumina.mapper.ProviderEndpointMapper;
import com.lumina.service.ProviderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderControllerTest {

    @Mock
    private ProviderService providerService;

    @Mock
    private ProviderEndpointMapper endpointMapper;

    @InjectMocks
    private ProviderController providerController;

    @Test
    void createProviderDerivesBaseUrlFromPrimaryEndpoint() {
        Provider provider = validProvider();
        provider.setBaseUrl("");
        provider.setType("4,0,2");
        provider.setEndpoints(List.of(
                endpoint(4, "https://api.openai.com"),
                endpoint(0, "https://api.minimaxi.com"),
                endpoint(2, "https://api.minimaxi.com/anthropic")
        ));

        when(providerService.save(any(Provider.class))).thenAnswer(invocation -> {
            Provider saved = invocation.getArgument(0);
            saved.setId(42L);
            return true;
        });

        ApiResponse<Provider> response = providerController.createProvider(provider);

        assertEquals(200, response.getCode());
        assertEquals("https://api.openai.com", response.getData().getBaseUrl());
        assertEquals(42L, response.getData().getEndpoints().get(0).getProviderId());
        assertEquals(42L, response.getData().getEndpoints().get(1).getProviderId());
        assertEquals(42L, response.getData().getEndpoints().get(2).getProviderId());
        verify(providerService).save(provider);
        verify(endpointMapper).insert(provider.getEndpoints().get(0));
        verify(endpointMapper).insert(provider.getEndpoints().get(1));
        verify(endpointMapper).insert(provider.getEndpoints().get(2));
    }

    @Test
    void createProviderRequiresBaseUrlWhenNoEndpointsProvided() {
        Provider provider = validProvider();
        provider.setBaseUrl("");
        provider.setEndpoints(List.of());

        assertThrows(IllegalArgumentException.class, () -> providerController.createProvider(provider));

        verify(providerService, never()).save(any());
    }

    private Provider validProvider() {
        Provider provider = new Provider();
        provider.setName("minimaxi");
        provider.setType("0");
        provider.setIsEnabled(true);
        provider.setModelName("MiniMax-M3");
        provider.setApiKey("sk-test");
        provider.setAutoSync(false);
        return provider;
    }

    private ProviderEndpoint endpoint(int protocolType, String baseUrl) {
        ProviderEndpoint endpoint = new ProviderEndpoint();
        endpoint.setProtocolType(protocolType);
        endpoint.setBaseUrl(baseUrl);
        return endpoint;
    }
}
