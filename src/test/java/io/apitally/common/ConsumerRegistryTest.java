package io.apitally.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apitally.common.dto.Consumer;

public class ConsumerRegistryTest {
    private ConsumerRegistry consumerRegistry;

    @BeforeEach
    void setUp() {
        consumerRegistry = new ConsumerRegistry();
    }

    @Test
    void testAddOrUpdateConsumer() {
        Consumer consumer = ConsumerRegistry.consumerFromObject("test");
        consumerRegistry.addOrUpdateConsumer(consumer);
        consumer = new Consumer("test", "Test 1", "Group 1");
        consumerRegistry.addOrUpdateConsumer(consumer);
        consumer = new Consumer("test", null, "Group 2");
        consumerRegistry.addOrUpdateConsumer(consumer);
        consumer = new Consumer("test", "Test 2", null);
        consumerRegistry.addOrUpdateConsumer(consumer);

        List<Consumer> consumers = consumerRegistry.getAndResetConsumers();
        assertEquals(1, consumers.size());
        assertEquals("test", consumers.get(0).getIdentifier());
        assertEquals("Test 2", consumers.get(0).getName());
        assertEquals("Group 2", consumers.get(0).getGroup());

        consumer = new Consumer("test", "Test 2", "Group 2");
        consumerRegistry.addOrUpdateConsumer(consumer);
        consumers = consumerRegistry.getAndResetConsumers();
        assertEquals(0, consumers.size());
    }

    @Test
    void testconsumerFromObject() {
        Consumer consumer = ConsumerRegistry.consumerFromObject("test");
        assertEquals("test", consumer.getIdentifier());
        assertNull(consumer.getName());
        assertNull(consumer.getGroup());

        consumer = ConsumerRegistry.consumerFromObject(new Consumer("test", "Test 1", "Group 1"));
        assertEquals("test", consumer.getIdentifier());
        assertEquals("Test 1", consumer.getName());
        assertEquals("Group 1", consumer.getGroup());

        consumer = ConsumerRegistry.consumerFromObject(123);
        assertEquals("123", consumer.getIdentifier());

        consumer = ConsumerRegistry.consumerFromObject(1.23);
        assertNull(consumer);

        consumer = ConsumerRegistry.consumerFromObject("");
        assertNull(consumer);

        consumer = ConsumerRegistry.consumerFromObject(null);
        assertNull(consumer);
    }
}
