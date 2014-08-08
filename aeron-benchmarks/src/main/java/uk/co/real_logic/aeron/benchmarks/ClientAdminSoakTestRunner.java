/*
 * Copyright 2014 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.aeron.benchmarks;

import uk.co.real_logic.aeron.Aeron;
import uk.co.real_logic.aeron.common.concurrent.AtomicBuffer;
import uk.co.real_logic.aeron.driver.MediaDriver;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Goal: test for resource leaks in the control/admin side of things.
 *
 * For infinite amount of time:
 *  1. Create a publication & a subscription.
 *  2. Send a few messages
 *  3. Close the publication & a subscription.
 *
 *  This differs from the MediaDriverAdminSoakTestRunner by keeping the same two client
 *  instances. It thus detects resource leaks in the client admin.
 */
public class ClientAdminSoakTestRunner
{
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final AtomicBuffer PUBLISHING_BUFFER = new AtomicBuffer(ByteBuffer.allocateDirect(256));

    public static void main(String[] args) throws Exception
    {
        SoakTestHelper.useSharedMemoryOnLinux();

        final MediaDriver driver = new MediaDriver();
        driver.invokeEmbedded();

        final Aeron publishingClient = Aeron.newClient(new Aeron.Context());
        final Aeron consumingClient = Aeron.newClient(new Aeron.Context());

        consumingClient.invoke(EXECUTOR);
        publishingClient.invoke(EXECUTOR);

        for (int i = 0; true; i++)
        {
            SoakTestHelper.exchangeMessagesBetweenClients(publishingClient, consumingClient, PUBLISHING_BUFFER);

            if ((i % 100) == 0)
            {
                System.out.println("Completed Iteration " + i);
            }

            Thread.yield();
        }
    }

}