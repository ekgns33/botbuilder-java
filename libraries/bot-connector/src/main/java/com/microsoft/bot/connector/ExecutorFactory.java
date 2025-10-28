// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for
// license information.

package com.microsoft.bot.connector;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * Provides a common Executor for Future operations.
 */
public final class ExecutorFactory {
    private ExecutorFactory() {

    }

    private static ForkJoinWorkerThreadFactory factory = new ForkJoinWorkerThreadFactory() {
        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            ForkJoinWorkerThread worker =
                ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("Bot-" + worker.getPoolIndex());
            return worker;
        }
    };

    private static ExecutorService defaultExecutor =
        new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 2, factory, null, false);

    private static Executor executor = defaultExecutor;

    /**
     * Provides an SDK wide ExecutorService for async calls.
     *
     * @return An ExecutorService.
     * @deprecated Use {@link #getExecutor()} instead.
     */
    @Deprecated
    public static ExecutorService getExecutor() {
        if (executor instanceof ExecutorService) {
            return (ExecutorService) executor;
        }
        return defaultExecutor;
    }

    /**
     * Provides the configured Executor for async calls.
     * This can be either a thread pool executor or a direct executor.
     *
     * @return An Executor.
     */
    public static Executor getAsyncExecutor() {
        return executor;
    }

    /**
     * Sets the Executor to be used for async operations.
     * Use {@link MoreExecutors#directExecutor()} to make the calling thread
     * execute the tasks directly without using a thread pool.
     *
     * @param withExecutor The Executor to use.
     */
    public static void setExecutor(Executor withExecutor) {
        if (withExecutor == null) {
            throw new IllegalArgumentException("Executor cannot be null");
        }
        executor = withExecutor;
    }

    /**
     * Configures the factory to use direct execution on the calling thread.
     * This eliminates thread pool overhead and makes the calling thread
     * handle all async operations directly.
     */
    public static void useDirectExecutor() {
        executor = MoreExecutors.directExecutor();
    }

    /**
     * Resets the executor to the default ForkJoinPool.
     */
    public static void useDefaultExecutor() {
        executor = defaultExecutor;
    }

    /**
     * Returns whether the factory is currently using direct executor mode.
     *
     * @return true if using direct executor, false otherwise.
     */
    public static boolean isUsingDirectExecutor() {
        return executor == MoreExecutors.directExecutor();
    }
}
