/*
 * Copyright 2014 - 2016 Real Logic Ltd.
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
package io.aeron;

import org.junit.Before;
import org.junit.Test;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FrameDescriptor;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.ReadablePosition;

import java.nio.ByteBuffer;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static io.aeron.logbuffer.LogBufferDescriptor.*;

public class PublicationTest
{
    private static final String CHANNEL = "udp://localhost:40124";
    private static final int STREAM_ID_1 = 2;
    private static final int SESSION_ID_1 = 13;
    private static final int TERM_ID_1 = 1;
    private static final int CORRELATION_ID = 2000;
    private static final int SEND_BUFFER_CAPACITY = 1024;

    private final ByteBuffer sendBuffer = ByteBuffer.allocateDirect(SEND_BUFFER_CAPACITY);
    private final UnsafeBuffer atomicSendBuffer = new UnsafeBuffer(sendBuffer);
    private final UnsafeBuffer logMetaDataBuffer = spy(new UnsafeBuffer(ByteBuffer.allocateDirect(LOG_META_DATA_LENGTH)));
    private final UnsafeBuffer[] termBuffers = new UnsafeBuffer[PARTITION_COUNT];
    private final UnsafeBuffer[] termMetaDataBuffers = new UnsafeBuffer[PARTITION_COUNT];
    private final UnsafeBuffer[] buffers = new UnsafeBuffer[(PARTITION_COUNT * 2) + 1];

    private final ClientConductor conductor = mock(ClientConductor.class);
    private final LogBuffers logBuffers = mock(LogBuffers.class);
    private final ReadablePosition publicationLimit = mock(ReadablePosition.class);
    private Publication publication;

    @Before
    public void setUp()
    {
        when(publicationLimit.getVolatile()).thenReturn(2L * SEND_BUFFER_CAPACITY);
        when(logBuffers.atomicBuffers()).thenReturn(buffers);
        when(logBuffers.termLength()).thenReturn(TERM_MIN_LENGTH);

        initialTermId(logMetaDataBuffer, TERM_ID_1);
        timeOfLastStatusMessage(logMetaDataBuffer, 0);

        for (int i = 0; i < PARTITION_COUNT; i++)
        {
            termBuffers[i] = new UnsafeBuffer(ByteBuffer.allocateDirect(TERM_MIN_LENGTH));
            termMetaDataBuffers[i] = new UnsafeBuffer(ByteBuffer.allocateDirect(TERM_META_DATA_LENGTH));

            buffers[i] = termBuffers[i];
            buffers[i + PARTITION_COUNT] = termMetaDataBuffers[i];
        }
        buffers[LOG_META_DATA_SECTION_INDEX] = logMetaDataBuffer;

        publication = new Publication(
            conductor,
            CHANNEL,
            STREAM_ID_1,
            SESSION_ID_1,
            publicationLimit,
            logBuffers,
            CORRELATION_ID);

        publication.incRef();

        initialiseTailWithTermId(termMetaDataBuffers[0], TERM_ID_1);
    }

    @Test
    public void shouldEnsureThePublicationIsOpenBeforeReadingPosition()
    {
        publication.close();
        assertThat(publication.position(), is(Publication.CLOSED));
    }

    @Test
    public void shouldEnsureThePublicationIsOpenBeforeOffer()
    {
        publication.close();
        assertTrue(publication.isClosed());
        assertThat(publication.offer(atomicSendBuffer), is(Publication.CLOSED));
    }

    @Test
    public void shouldEnsureThePublicationIsOpenBeforeClaim()
    {
        publication.close();
        final BufferClaim bufferClaim = new BufferClaim();
        assertThat(publication.tryClaim(SEND_BUFFER_CAPACITY, bufferClaim), is(Publication.CLOSED));
    }

    @Test
    public void shouldReportThatPublicationHasNotBeenConnectedYet()
    {
        when(publicationLimit.getVolatile()).thenReturn(0L);
        when(conductor.isPublicationConnected(anyLong())).thenReturn(false);
        assertFalse(publication.isConnected());
    }

    @Test
    public void shouldReportThatPublicationHasBeenConnectedYet()
    {
        when(conductor.isPublicationConnected(anyLong())).thenReturn(true);
        assertTrue(publication.isConnected());
    }

    @Test
    public void shouldReportInitialPosition()
    {
        assertThat(publication.position(), is(0L));
    }

    @Test
    public void shouldReportMaxMessageLength()
    {
        assertThat(publication.maxMessageLength(), is(FrameDescriptor.computeMaxMessageLength(TERM_MIN_LENGTH)));
    }

    @Test
    public void shouldNotUnmapBuffersBeforeLastRelease() throws Exception
    {
        publication.incRef();
        publication.close();

        verify(logBuffers, never()).close();
    }

    @Test
    public void shouldUnmapBuffersWithMultipleReferences() throws Exception
    {
        publication.incRef();
        publication.close();

        publication.close();
        verify(conductor, times(1)).releasePublication(publication);
    }

    @Test
    public void shouldReleaseResourcesIdempotently() throws Exception
    {
        publication.close();
        publication.close();

        verify(conductor, times(1)).releasePublication(publication);
    }
}
