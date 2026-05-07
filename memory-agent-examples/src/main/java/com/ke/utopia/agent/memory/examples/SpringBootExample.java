package com.ke.utopia.agent.memory.examples;

/**
 * Spring Boot Starter 使用示例。
 *
 * <p>在实际 Spring Boot 项目中，只需：
 *
 * <p>1. 添加依赖：
 * <pre>
 * &lt;dependency&gt;
 *     &lt;groupId&gt;com.ke.utopia.agent&lt;/groupId&gt;
 *     &lt;artifactId&gt;memory-agent-spring-boot-starter&lt;/artifactId&gt;
 *     &lt;version&gt;1.0.0-SNAPSHOT&lt;/version&gt;
 * &lt;/dependency&gt;
 * </pre>
 *
 * <p>2. 配置 application.yml：
 * <pre>
 * memory-agent:
 *   enabled: true
 *   storage-type: in-memory
 *   memory-char-limit: 2200
 *   summarization-message-threshold: 10
 *   summarize-on-session-close: true
 *   llm:
 *     api-key: ${OPENAI_API_KEY}
 *     model: gpt-4o-mini
 *     base-url: https://api.openai.com/v1
 *     max-tokens: 2000
 *     temperature: 0.3
 * </pre>
 *
 * <p>3. 在代码中注入使用：
 * <pre>
 * &#64;RestController
 * &#64;RequestMapping("/api/chat")
 * public class ChatController {
 *
 *     &#64;Autowired
 *     private MemoryAgent memoryAgent;
 *
 *     &#64;PostMapping("/session")
 *     public Session createSession(&#64;RequestParam String userId) {
 *         return memoryAgent.createSession(userId, "web");
 *     }
 *
 *     &#64;PostMapping("/message")
 *     public ConversationMessage addMessage(
 *             &#64;RequestParam String sessionId,
 *             &#64;RequestParam String role,
 *             &#64;RequestParam String content) {
 *         if ("user".equals(role)) {
 *             return memoryAgent.addUserMessage(sessionId, content);
 *         } else {
 *             return memoryAgent.addAssistantMessage(sessionId, content);
 *         }
 *     }
 *
 *     &#64;GetMapping("/summary")
 *     public IntentSummary getSummary(&#64;RequestParam String sessionId) {
 *         return memoryAgent.summarize(sessionId);
 *     }
 *
 *     &#64;GetMapping("/history")
 *     public List&lt;ConversationMessage&gt; getHistory(&#64;RequestParam String sessionId) {
 *         return memoryAgent.getMessages(sessionId);
 *     }
 *
 *     &#64;DeleteMapping("/session")
 *     public Session closeSession(&#64;RequestParam String sessionId) {
 *         return memoryAgent.closeSession(sessionId);
 *     }
 * }
 * </pre>
 *
 * <p>4. 自定义存储（可选）：
 * <pre>
 * &#64;Configuration
 * public class MyStorageConfig {
 *
 *     &#64;Bean
 *     public MemoryStorage memoryStorage(DataSource dataSource) {
 *         return new MyMySQLMemoryStorage(dataSource);
 *     }
 *
 *     &#64;Bean
 *     public IntentSummarizer intentSummarizer() {
 *         return new MyClaudeSummarizer();
 *     }
 * }
 * </pre>
 *
 * <p>自定义 Bean 会自动替代默认实现（@ConditionalOnMissingBean）。
 */
public class SpringBootExample {
    // This class serves as documentation only.
    // See class-level Javadoc for usage instructions.
}
