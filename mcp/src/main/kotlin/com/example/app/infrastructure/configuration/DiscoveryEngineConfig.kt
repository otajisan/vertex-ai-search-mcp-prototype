package com.example.app.infrastructure.configuration

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Discovery Engine 関連の設定。
 * SearchServiceClient は SearchServiceClientHolder 内で遅延生成するため、ここでは Bean を定義しない。
 */
@Configuration
@Profile("!test")
class DiscoveryEngineConfig
