package com.cloudcostai.aws.service.impl;

import com.cloudcostai.aws.config.AwsClientFactory;
import com.cloudcostai.aws.exception.AwsConnectionException;
import com.cloudcostai.aws.service.AwsConnectionSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AwsResourceServiceImplTest {
    @Mock
    private AwsClientFactory clientFactory;

    @Mock
    private AwsConnectionSessionService sessionService;

    @Test
    void rejectsMissingConnectionBeforeCreatingAwsClients() {
        AwsResourceServiceImpl service = new AwsResourceServiceImpl(clientFactory, sessionService);
        when(sessionService.getSession(null)).thenThrow(new AwsConnectionException("AWS connection is not available. Please connect an AWS account first."));

        assertThatThrownBy(() -> service.discoverResources(null))
                .isInstanceOf(AwsConnectionException.class)
                .hasMessageContaining("connection is not available");
        verifyNoInteractions(clientFactory);
    }
}
