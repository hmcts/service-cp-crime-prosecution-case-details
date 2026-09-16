package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;
import uk.gov.hmcts.cp.security.TestJwksConfig;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwksConfig.class)
@TestPropertySource(properties = {
    "auth.mode=ENFORCE",
    "auth.tenant-id=11111111-1111-1111-1111-111111111111",
    "auth.audience=22222222-2222-2222-2222-222222222222",
    "auth.roles=app.read"
})
@Slf4j
public abstract class IntegrationTestBase {

    @Autowired
    AppPropertiesBackend appProperties;

    @Resource
    protected MockMvc mockMvc;
}
