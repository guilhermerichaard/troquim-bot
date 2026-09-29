package com.troquim_bot.owner.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OwnerPasskeyProperties.class)
public class OwnerPasskeyConfiguration {}
