package com.ticketflow.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DemoAgentSeederConfigurationTest {

    private final AgentRepository repository = mock(AgentRepository.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AgentRepository.class, () -> repository)
            .withUserConfiguration(DemoAgentSeeder.class);

    @Test
    void shouldBeDisabledWhenPropertyIsAbsent() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(DemoAgentSeeder.class);
            verifyNoInteractions(repository);
        });
    }

    @Test
    void shouldBeDisabledWhenPropertyIsFalse() {
        contextRunner.withPropertyValues("ticketflow.demo-seeding.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DemoAgentSeeder.class);
                    verifyNoInteractions(repository);
                });
    }

    @Test
    void shouldBeEnabledOnlyWhenExplicitlyTrue() {
        contextRunner.withPropertyValues("ticketflow.demo-seeding.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(DemoAgentSeeder.class);
                    context.getBean(DemoAgentSeeder.class)
                            .run(new DefaultApplicationArguments(new String[0]));
                    verify(repository, times(3)).insert(any(Agent.class));
                });
    }
}
