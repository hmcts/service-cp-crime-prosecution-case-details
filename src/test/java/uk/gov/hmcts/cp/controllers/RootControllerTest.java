package uk.gov.hmcts.cp.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.filters.jwt.AuthDetails;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RootControllerTest {

    @Mock
    AuthDetails authDetails;

    @InjectMocks
    RootController rootController;

    @Test
    void root_controller_should_return_ok() {
        ResponseEntity<String> response = rootController.getRoot();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}