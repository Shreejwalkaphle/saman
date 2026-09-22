package com.bajar.saman;

import com.bajar.saman.config.AdminBootstrapConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class SamanApplicationTests {

	@MockitoBean
	private ProxyManager<byte[]> rateLimitProxyManager;

	@MockitoBean
	private AdminBootstrapConfig adminBootstrapConfig;

	@Test
	void contextLoads() {
	}

}
