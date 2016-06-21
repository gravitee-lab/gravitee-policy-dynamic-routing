/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.dynamicrouting;

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.el.SpelTemplateEngine;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.dynamicrouting.configuration.DynamicRoutingPolicyConfiguration;
import io.gravitee.policy.dynamicrouting.configuration.Rule;
import io.gravitee.reporter.api.http.RequestMetrics;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author David BRASSELY (david at gravitee.io)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicRoutingPolicyTest {

    private DynamicRoutingPolicy dynamicRoutingPolicy;

    @Mock
    private DynamicRoutingPolicyConfiguration dynamicRoutingPolicyConfiguration;

    @Mock
    protected Request request;

    @Mock
    protected Response response;

    @Mock
    protected PolicyChain policyChain;

    @Mock
    protected ExecutionContext executionContext;

    @Before
    public void init() {
        initMocks(this);

        dynamicRoutingPolicy = new DynamicRoutingPolicy(dynamicRoutingPolicyConfiguration);
        when(request.metrics()).thenReturn(RequestMetrics.on(System.currentTimeMillis()).build());
    }

    @Test
    public void test_shouldThrowFailure_noRule() {
        // Prepare inbound request
        final HttpHeaders headers = new HttpHeaders();
        when(request.headers()).thenReturn(headers);
        when(request.path()).thenReturn("/products/ecom/");

        // Prepare context
        when(executionContext.getAttribute(ExecutionContext.ATTR_RESOLVED_PATH)).thenReturn("/ecom/");

        // Execute policy
        dynamicRoutingPolicy.onRequest(request, response, executionContext, policyChain);

        // Check results
        verify(policyChain).failWith(any(PolicyResult.class));
    }

    @Test
    public void test_shouldDynamicRouting_singleMatchingRule() {
        // Prepare policy configuration
        List<Rule> rules = new ArrayList<>();
        rules.add(new Rule(Pattern.compile("/ecom/"), "http://host1/product"));

        when(dynamicRoutingPolicyConfiguration.getRules()).thenReturn(rules);

        // Prepare inbound request
        final HttpHeaders headers = new HttpHeaders();
        when(request.headers()).thenReturn(headers);
        when(request.path()).thenReturn("/products/v1/ecom/");

        // Prepare context
        when(executionContext.getAttribute(ExecutionContext.ATTR_RESOLVED_PATH)).thenReturn("/ecom/");
        when(executionContext.getTemplateEngine()).thenReturn(new SpelTemplateEngine());

        // Execute policy
        dynamicRoutingPolicy.onRequest(request, response, executionContext, policyChain);

        // Check results
        verify(policyChain).doNext(request, response);
        verify(executionContext).setAttribute(ExecutionContext.ATTR_REQUEST_ENDPOINT, rules.iterator().next().getUrl());
    }

    @Test
    public void test_shouldDynamicRouting_multipleMatchingRule() {
        // Prepare policy configuration
        List<Rule> rules = new ArrayList<>();
        rules.add(new Rule(Pattern.compile("/ecom/"), "http://host1/product"));
        rules.add(new Rule(Pattern.compile("/ecom/subpath"), "http://host2/product"));

        when(dynamicRoutingPolicyConfiguration.getRules()).thenReturn(rules);

        // Prepare inbound request
        final HttpHeaders headers = new HttpHeaders();
        when(request.headers()).thenReturn(headers);
        when(request.path()).thenReturn("/products/v1/ecom/");

        // Prepare context
        when(executionContext.getAttribute(ExecutionContext.ATTR_RESOLVED_PATH)).thenReturn("/ecom/");
        when(executionContext.getTemplateEngine()).thenReturn(new SpelTemplateEngine());

        // Execute policy
        dynamicRoutingPolicy.onRequest(request, response, executionContext, policyChain);

        // Check results
        verify(policyChain).doNext(request, response);
        verify(executionContext).setAttribute(ExecutionContext.ATTR_REQUEST_ENDPOINT, rules.get(0).getUrl());
    }

    @Test
    public void test_shouldDynamicRouting_multipleMatchingRule_regex() {
        // Prepare policy configuration
        List<Rule> rules = new ArrayList<>();
        rules.add(new Rule(Pattern.compile("/ecome.*"), "http://host1/product"));
        rules.add(new Rule(Pattern.compile("/ecom/(.*)"), "http://host2/product"));

        when(dynamicRoutingPolicyConfiguration.getRules()).thenReturn(rules);

        // Prepare inbound request
        final HttpHeaders headers = new HttpHeaders();
        when(request.headers()).thenReturn(headers);
        when(request.path()).thenReturn("/products/v1/ecom/");

        // Prepare context
        when(executionContext.getAttribute(ExecutionContext.ATTR_RESOLVED_PATH)).thenReturn("/ecom/");
        when(executionContext.getTemplateEngine()).thenReturn(new SpelTemplateEngine());

        // Execute policy
        dynamicRoutingPolicy.onRequest(request, response, executionContext, policyChain);

        // Check results
        verify(policyChain).doNext(request, response);
        verify(executionContext).setAttribute(ExecutionContext.ATTR_REQUEST_ENDPOINT, rules.get(1).getUrl());
    }

    @Test
    public void test_shouldDynamicRouting_multipleMatchingRule_transformEndpoint() {
        // Prepare policy configuration
        List<Rule> rules = new ArrayList<>();
        rules.add(new Rule(Pattern.compile("/ecome.*"), "http://host1/product"));
        rules.add(new Rule(Pattern.compile("/ecom/(.*)"), "http://host2/product/{#group_0}"));

        when(dynamicRoutingPolicyConfiguration.getRules()).thenReturn(rules);

        // Prepare inbound request
        final HttpHeaders headers = new HttpHeaders();
        when(request.headers()).thenReturn(headers);
        when(request.path()).thenReturn("/products/v1/ecom/search");

        // Prepare context
        when(executionContext.getAttribute(ExecutionContext.ATTR_RESOLVED_PATH)).thenReturn("/ecom/search");
        when(executionContext.getTemplateEngine()).thenReturn(new SpelTemplateEngine());

        // Execute policy
        dynamicRoutingPolicy.onRequest(request, response, executionContext, policyChain);

        // Check results
        verify(policyChain).doNext(request, response);
        verify(executionContext).setAttribute(ExecutionContext.ATTR_REQUEST_ENDPOINT, "http://host2/product/search");
    }
}
