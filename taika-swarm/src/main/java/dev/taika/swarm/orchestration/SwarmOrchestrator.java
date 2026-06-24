package dev.taika.swarm.orchestration;

import dev.taika.agent.service.AgentService;
import dev.taika.api.AgentResult;
import dev.taika.api.AgentType;
import dev.taika.swarm.orchestration.model.AgentInvocationLog;
import dev.taika.swarm.orchestration.model.ExecutionThread;
import dev.taika.swarm.orchestration.model.SwarmResult;
import dev.taika.swarm.orchestration.model.SwarmStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class SwarmOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(SwarmOrchestrator.class);

    private final AgentService agentService;

    public SwarmOrchestrator(AgentService agentService) {
        this.agentService = agentService;
    }

    public SwarmResult orchestrate(Path projectPath, String mainObjective) {
        logger.info("Starting swarm orchestration for project '{}' with objective: {}", projectPath, mainObjective);

        ExecutionThread thread = new ExecutionThread();

        // Step 1: Invoke CODER agent
        logger.info("Invoking CODER agent...");
        AgentResult coderResult = agentService.executeTask(projectPath, AgentType.CODER, mainObjective);
        thread.log(new AgentInvocationLog(AgentType.CODER, coderResult.status()));

        // For now, we'll just return the result of the first step.
        // In the future, this will be a loop that calls TESTER, VALIDATOR, etc.
        logger.info("Initial CODER step finished. Swarm orchestration complete for now.");
        return new SwarmResult(thread.getId(), SwarmStatus.SUCCESS, thread.getHistory(), "Initial coding phase completed.");
    }
}