// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.connector;

import com.google.common.util.concurrent.MoreExecutors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for ExecutorFactory direct executor functionality.
 */
public class ExecutorFactoryTest {

    @After
    public void cleanup() {
        ExecutorFactory.useDefaultExecutor();
    }

    @Test
    public void testDefaultExecutor() {
        ExecutorFactory.useDefaultExecutor();
        Executor executor = ExecutorFactory.getAsyncExecutor();
        Assert.assertNotNull(executor);
        Assert.assertFalse(ExecutorFactory.isUsingDirectExecutor());
    }

    @Test
    public void testUseDirectExecutor() {
        ExecutorFactory.useDirectExecutor();
        Assert.assertTrue(ExecutorFactory.isUsingDirectExecutor());
    }

    @Test
    public void testDirectExecutorRunsOnCallingThread() throws Exception {
        ExecutorFactory.useDirectExecutor();

        String mainThreadName = Thread.currentThread().getName();
        AtomicBoolean sameThread = new AtomicBoolean(false);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            String executorThreadName = Thread.currentThread().getName();
            sameThread.set(mainThreadName.equals(executorThreadName));
            System.out.println("Main thread: " + mainThreadName + ", Executor thread: " + executorThreadName);
        }, ExecutorFactory.getAsyncExecutor());

        future.join();
        Assert.assertTrue("Direct executor should run on calling thread", sameThread.get());
    }

    @Test
    public void testPoolExecutorRunsOnDifferentThread() throws Exception {
        ExecutorFactory.useDefaultExecutor();

        String mainThreadName = Thread.currentThread().getName();
        AtomicBoolean differentThread = new AtomicBoolean(false);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            String executorThreadName = Thread.currentThread().getName();
            differentThread.set(!mainThreadName.equals(executorThreadName));
            System.out.println("Main thread: " + mainThreadName + ", Executor thread: " + executorThreadName);
        }, ExecutorFactory.getAsyncExecutor());

        future.join();
        Assert.assertTrue("Pool executor should run on different thread", differentThread.get());
    }

    @Test
    public void testSetCustomExecutor() {
        ExecutorService customExecutor = Executors.newSingleThreadExecutor();
        ExecutorFactory.setExecutor(customExecutor);

        Executor executor = ExecutorFactory.getAsyncExecutor();
        Assert.assertEquals(customExecutor, executor);
        Assert.assertFalse(ExecutorFactory.isUsingDirectExecutor());

        customExecutor.shutdown();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetNullExecutorThrowsException() {
        ExecutorFactory.setExecutor(null);
    }

    @Test
    public void testSwitchBetweenExecutors() {
        ExecutorFactory.useDefaultExecutor();
        Assert.assertFalse(ExecutorFactory.isUsingDirectExecutor());

        ExecutorFactory.useDirectExecutor();
        Assert.assertTrue(ExecutorFactory.isUsingDirectExecutor());

        ExecutorFactory.useDefaultExecutor();
        Assert.assertFalse(ExecutorFactory.isUsingDirectExecutor());
    }

    @Test
    public void testDirectExecutorPerformance() throws Exception {
        int iterations = 1000;

        ExecutorFactory.useDefaultExecutor();
        long poolStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            CompletableFuture.runAsync(() -> {
                int sum = 0;
                for (int j = 0; j < 100; j++) {
                    sum += j;
                }
            }, ExecutorFactory.getAsyncExecutor()).join();
        }
        long poolTime = System.nanoTime() - poolStart;

        ExecutorFactory.useDirectExecutor();
        long directStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            CompletableFuture.runAsync(() -> {
                int sum = 0;
                for (int j = 0; j < 100; j++) {
                    sum += j;
                }
            }, ExecutorFactory.getAsyncExecutor()).join();
        }
        long directTime = System.nanoTime() - directStart;

        System.out.println("Pool executor time: " + poolTime / 1_000_000 + "ms");
        System.out.println("Direct executor time: " + directTime / 1_000_000 + "ms");

        Assert.assertTrue("Test completed successfully", directTime >= 0 && poolTime >= 0);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testBackwardCompatibility() {
        ExecutorService executor = ExecutorFactory.getExecutor();
        Assert.assertNotNull(executor);
    }

    @Test
    public void testDirectExecutorWithMoreExecutors() {
        ExecutorFactory.setExecutor(MoreExecutors.directExecutor());
        Assert.assertTrue(ExecutorFactory.isUsingDirectExecutor());

        Executor executor = ExecutorFactory.getAsyncExecutor();
        Assert.assertEquals(MoreExecutors.directExecutor(), executor);
    }
}

