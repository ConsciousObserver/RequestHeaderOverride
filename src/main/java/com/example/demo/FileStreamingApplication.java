package com.example.demo;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
public class FileStreamingApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileStreamingApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * This is needed as other options like adding @Order annotation on the
     * filter bean places the filter after some Tomcat and Spring filters, by
     * that time headers are already cached by Spring and cannot be overridden
     * 
     * @return
     */
    @Bean
    FilterRegistrationBean<HeaderOverrideFilter> headerOerrideFilterRegistration() {

        FilterRegistrationBean<HeaderOverrideFilter> filterRegistrationBean = new FilterRegistrationBean<>(
                new HeaderOverrideFilter());

        filterRegistrationBean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);

        /*
         * -------------------------------------------------------------------------------------------------------
         * This is extremely important. We want this filter to register itself even before any Tomcat filters.
         * -------------------------------------------------------------------------------------------------------
         */
        filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        log.info("Configured HeaderOverrideFilter ***********************************************");

        return filterRegistrationBean;
    }
}

@RestController
@RequestMapping("/")
class TestController {

    @GetMapping("/hello")
    public String hello(
            @RequestHeader("foo") List<String> overridenHeaderFoo,
            @RequestHeader("bar") String fixedHeaderBar) {

        return String.format("foo=%s, bar=%s", overridenHeaderFoo, fixedHeaderBar);
    }
}

class HeaderOverrideFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Set<String> supportedHeadersToOverride = Set.of("foo");

        LinkedCaseInsensitiveMap<String> fixedValueHeaders = new LinkedCaseInsensitiveMap<>();
        fixedValueHeaders.put("bar", "bar-FIXED-VALUE");

        HeaderOverrideRequestWrapper wrappedRequest = new HeaderOverrideRequestWrapper(request,
                supportedHeadersToOverride,
                "-OVERRIDDEN", fixedValueHeaders);

        filterChain.doFilter(wrappedRequest, response);
    }

}