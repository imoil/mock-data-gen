package com.samsung.ees.infra.mock.starter.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.samsung.ees.infra.mock.core.config.GeneratorConfig;
import com.samsung.ees.infra.mock.core.engine.DeterministicMockGenerator;
import com.samsung.ees.infra.mock.starter.controller.MockSensorController;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

class MockHeaderOperationCustomizerTest {

    private final MockHeaderOperationCustomizer customizer = new MockHeaderOperationCustomizer();

    @Test
    void addsOptionalHeaderParameterForMockSensorControllerMethods() throws NoSuchMethodException {
        MockSensorController controller = new MockSensorController(
                new DeterministicMockGenerator(GeneratorConfig.defaults())
        );
        Method currentMethod = MockSensorController.class.getMethod("getCurrentMockValues", List.class, Long.class);
        Method seriesMethod = MockSensorController.class.getMethod(
                "getTimeSeriesMockValues",
                List.class,
                String.class,
                String.class,
                Long.class
        );

        for (Method method : List.of(currentMethod, seriesMethod)) {
            Operation operation = new Operation();
            customizer.customize(operation, new HandlerMethod(controller, method));

            assertThat(operation.getParameters()).hasSize(1);
            Parameter headerParam = operation.getParameters().get(0);
            assertThat(headerParam.getName()).isEqualTo(MockSensorController.MOCK_HEADER_NAME);
            assertThat(headerParam.getIn()).isEqualTo("header");
            assertThat(headerParam.getRequired()).isFalse();
            assertThat(headerParam.getExample()).isEqualTo("true");
            assertThat(headerParam.getSchema().getDefault()).isEqualTo("true");
            assertThat(headerParam.getDescription()).isNotBlank();
        }
    }

    @Test
    void isIdempotentWhenMockHeaderAlreadyPresent() throws NoSuchMethodException {
        MockSensorController controller = new MockSensorController(
                new DeterministicMockGenerator(GeneratorConfig.defaults())
        );
        Method currentMethod = MockSensorController.class.getMethod("getCurrentMockValues", List.class, Long.class);
        HandlerMethod handlerMethod = new HandlerMethod(controller, currentMethod);

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
    void skipsNonMockSensorControllerMethods() throws NoSuchMethodException {
        OperationalDummyController operationalController = new OperationalDummyController();
        Method method = OperationalDummyController.class.getMethod("getCurrentValues");
        HandlerMethod handlerMethod = new HandlerMethod(operationalController, method);

        Operation operation = new Operation();
        customizer.customize(operation, handlerMethod);

        assertThat(operation.getParameters()).isNull();
    }

    static class OperationalDummyController {

        public String getCurrentValues() {
            return "ok";
        }
    }
}
