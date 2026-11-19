package com.samsung.ees.infra.mock.starter.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.method.HandlerMethod;

class MockHeaderOperationCustomizerTest {

    private final MockHeaderOperationCustomizer customizer = new MockHeaderOperationCustomizer();

    @Test
    void addsOptionalHeaderParameterForMockRoutedOperations() throws NoSuchMethodException {
        MockDummyController mockController = new MockDummyController();
        for (String methodName : new String[]{"current", "timeseries"}) {
            Method method = MockDummyController.class.getMethod(methodName);
            Operation operation = new Operation();
            customizer.customize(operation, new HandlerMethod(mockController, method));

            assertThat(operation.getParameters()).hasSize(1);
            Parameter headerParam = operation.getParameters().get(0);
            assertThat(headerParam.getName()).isEqualTo(MockHeaderOperationCustomizer.MOCK_HEADER_NAME);
            assertThat(headerParam.getIn()).isEqualTo("header");
            assertThat(headerParam.getRequired()).isFalse();
            assertThat(headerParam.getExample()).isEqualTo("true");
            assertThat(headerParam.getSchema().getDefault()).isEqualTo("true");
            assertThat(headerParam.getDescription()).isNotBlank();
        }
    }

    @Test
    void isIdempotentWhenMockHeaderAlreadyPresent() throws NoSuchMethodException {
        MockDummyController mockController = new MockDummyController();
        Method method = MockDummyController.class.getMethod("current");
        HandlerMethod handlerMethod = new HandlerMethod(mockController, method);

        Operation operation = new Operation();
        operation.addParametersItem(new Parameter().in("header").name("x-mock-data"));

        customizer.customize(operation, handlerMethod);
        customizer.customize(operation, handlerMethod);

        assertThat(operation.getParameters()).hasSize(1);
        Parameter headerParam = operation.getParameters().get(0);
        assertThat(headerParam.getIn()).isEqualTo("header");
        assertThat(headerParam.getRequired()).isFalse();
        assertThat(headerParam.getExample()).isEqualTo("true");
        assertThat(headerParam.getDescription()).isNotBlank();
    }

    @Test
    void skipsOperationsWithoutMockHeaderRouting() throws NoSuchMethodException {
        OperationalDummyController operationalController = new OperationalDummyController();
        Method method = OperationalDummyController.class.getMethod("getCurrentValues");
        HandlerMethod handlerMethod = new HandlerMethod(operationalController, method);

        Operation operation = new Operation();
        customizer.customize(operation, handlerMethod);

        assertThat(operation.getParameters()).isNull();
    }

    static class MockDummyController {

        @GetMapping(value = "/current", headers = "X-Mock-Data=true")
        public String current() {
            return "mock-current";
        }

        @GetMapping(value = "/timeseries", headers = "X-Mock-Data=true")
        public String timeseries() {
            return "mock-series";
        }
    }

    static class OperationalDummyController {

        @GetMapping("/current")
        public String getCurrentValues() {
            return "ok";
        }
    }
}
