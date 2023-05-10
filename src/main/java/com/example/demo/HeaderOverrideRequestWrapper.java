package com.example.demo;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMessage;
import org.springframework.util.LinkedCaseInsensitiveMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * 
 * A request wrapper that can be used to override value of some headers with
 * their counter parts with a suffix. For example, HOST header value can be
 * overridden in HOST-XXX header. It also supports constant values for some
 * headers.
 */
public class HeaderOverrideRequestWrapper extends HttpServletRequestWrapper implements HttpMessage {

    private final Set<String> supportedHeadersLowercase;

    private final LinkedCaseInsensitiveMap<String> fixedHeaders;

    private final Map<String, String> nameToOverriden;

    private final Map<String, String> overridenToActualName;

    private final Set<String> overriddenHeaders;

    /**
     * Cached value of headers. It's used in {@link #getHeaders()}
     */
    private HttpHeaders headers;

    /**
     * @param request
     *            request being wrapped by this wrapper
     * 
     * @param supportedHeadersLowercase
     *            list of <b>lower case> header name that can be overridden.
     *            It's being limited for security reasons, so that only those
     *            headers that we know are being handled at load balancer are
     *            overridden.
     * 
     * @param overrideSuffix
     *            suffix for the overridden header value. If suffix is -XXX then
     *            value of a header like Content-Type will be overridden by
     *            another header named Content-Type-XXX, given that Content-Type
     *            is part of supportedHeaders
     * 
     * @param fixedHeaders
     *            Contains constant values for some headers
     */
    public HeaderOverrideRequestWrapper(
            HttpServletRequest request,
            Set<String> supportedHeadersLowercase,
            String overrideSuffix,
            LinkedCaseInsensitiveMap<String> fixedHeaders) {

        super(request);

        this.supportedHeadersLowercase = supportedHeadersLowercase;
        this.fixedHeaders = fixedHeaders;

        //Following maps are used to avoid repeated string concatenations
        nameToOverriden = supportedHeadersLowercase.stream()
                .collect(Collectors.toMap(Function.identity(), k -> (k + overrideSuffix).toLowerCase()));

        overridenToActualName = nameToOverriden.entrySet()
                .stream()
                .collect(Collectors.toMap(Entry::getValue, Entry::getKey));

        overriddenHeaders = new HashSet<>(nameToOverriden.values());
    }

    /**
     * Returns value from following sources in that order
     * 
     * <ol>
     * <li>Fixed value if present</li>
     * <li>Overridden header value if supported and present in request</li>
     * <li>Original header value</li>
     * </ol>
     */
    @Override
    public String getHeader(String name) {
        //Constant value
        String value = fixedHeaders.get(name);

        if (value == null) {
            String overriddenName = nameToOverriden.get(name);

            //Original header value
            value = super.getHeader(name);

            if (shouldOverride(name)) {
                value = super.getHeader(overriddenName);
            }
        }

        return value;
    }

    /**
     * Returns value from following sources in that order.
     * 
     * <ol>
     * <li>Fixed value if present</li>
     * <li>Overridden header value if supported and present in request</li>
     * <li>Original header value</li>
     * </ol>
     */
    @Override
    public Enumeration<String> getHeaders(String name) {
        //Constant value
        Enumeration<String> values = null;

        if (fixedHeaders.containsKey(name)) {
            values = Collections.enumeration(List.of(fixedHeaders.get(name)));
        } else {
            String overriddenName = nameToOverriden.get(name);

            //Original header value
            values = super.getHeaders(name);

            if (shouldOverride(name)) {
                values = super.getHeaders(overriddenName);
            }
        }

        return values;
    }

    /**
     * Returns all header names from the request. If overridden header is part
     * of request, it returns actual header name.
     * 
     * 
     */
    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> newNames = new LinkedHashSet<>();

        super.getHeaderNames().asIterator().forEachRemaining(name -> {
            name = name.toLowerCase();

            if (overriddenHeaders.contains(name)) {
                newNames.add(overridenToActualName.get(name));
            } else {
                newNames.add(name);
            }
        });

        //Fixed headers may not be in the request, in that case we have to provide them ourself
        newNames.addAll(fixedHeaders.keySet());

        return Collections.enumeration(newNames);
    }

    /**
     * This method is used in many Spring classes from {@link HttpMessage} and
     * must be overridden.
     */
    @Override
    public HttpHeaders getHeaders() {

        if (this.headers == null) {
            this.headers = new HttpHeaders();

            super.getHeaderNames().asIterator().forEachRemaining(name -> {

                name = name.toLowerCase();

                String headerName = name;

                Enumeration<String> values = null;

                if (overriddenHeaders.contains(name)) {
                    headerName = overridenToActualName.get(name);

                    values = this.getHeaders(name);
                } else {
                    values = super.getHeaders(headerName);
                }

                while (values.hasMoreElements()) {
                    headers.add(headerName, values.nextElement());
                }
            });
        }

        return this.headers;
    }

    boolean shouldOverride(String name) {
        return supportedHeadersLowercase.contains(name.toLowerCase())
                && super.getHeader(nameToOverriden.get(name.toLowerCase())) != null;
    }
}
