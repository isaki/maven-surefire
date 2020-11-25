package org.apache.maven.surefire.booter.stream;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.plugin.surefire.log.api.ConsoleLogger;
import org.apache.maven.surefire.api.booter.Command;
import org.apache.maven.surefire.api.booter.ForkedProcessEventType;
import org.apache.maven.surefire.api.booter.MasterProcessCommand;
import org.apache.maven.surefire.api.booter.Shutdown;
import org.apache.maven.surefire.api.fork.ForkNodeArguments;
import org.apache.maven.surefire.api.report.RunMode;
import org.apache.maven.surefire.api.stream.AbstractStreamDecoder;
import org.apache.maven.surefire.api.stream.MalformedChannelException;
import org.apache.maven.surefire.api.stream.SegmentType;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;

import static org.apache.maven.surefire.api.booter.Command.BYE_ACK;
import static org.apache.maven.surefire.api.booter.Command.NOOP;
import static org.apache.maven.surefire.api.booter.Command.SKIP_SINCE_NEXT_TEST;
import static org.apache.maven.surefire.api.booter.Command.TEST_SET_FINISHED;
import static org.apache.maven.surefire.api.booter.Command.toRunClass;
import static org.apache.maven.surefire.api.booter.Command.toShutdown;
import static org.apache.maven.surefire.api.booter.Constants.MAGIC_NUMBER_FOR_COMMANDS_BYTES;
import static org.apache.maven.surefire.api.stream.SegmentType.DATA_STRING;
import static org.apache.maven.surefire.api.stream.SegmentType.END_OF_FRAME;
import static org.apache.maven.surefire.api.stream.SegmentType.STRING_ENCODING;

/**
 *
 */
public class CommandDecoder extends AbstractStreamDecoder<Command, MasterProcessCommand, SegmentType>
{
    private static final int NO_POSITION = -1;

    private static final SegmentType[] COMMAND_WITHOUT_DATA = new SegmentType[] {
        END_OF_FRAME
    };

    private static final SegmentType[] COMMAND_WITH_ONE_STRING = new SegmentType[] {
        STRING_ENCODING,
        DATA_STRING,
        END_OF_FRAME
    };

    private final Map<Segment, RunMode> runModes;
    private final ForkNodeArguments arguments;

    public CommandDecoder( @Nonnull ReadableByteChannel channel,
                           @Nonnull Map<Segment, MasterProcessCommand> commandTypes,
                           @Nonnull Map<Segment, RunMode> runModes,
                           @Nonnull ConsoleLogger logger,
                           @Nonnull ForkNodeArguments arguments )
    {
        super( channel, arguments, commandTypes, logger );
        this.runModes = runModes;
        this.arguments = arguments;
    }

    @Nonnull
    @Override
    public Command decode( @Nonnull Memento memento ) throws IOException, MalformedChannelException
    {
        try
        {
            MasterProcessCommand commandType = readMessageType( memento );
            if ( commandType == null )
            {
                throw new MalformedFrameException( memento.getLine().getPositionByteBuffer(),
                    memento.getByteBuffer().position() );
            }
            RunMode runMode = null;
            for ( SegmentType segmentType : nextSegmentType( commandType ) )
            {
                switch ( segmentType )
                {
                    case RUN_MODE:
                        runMode = runModes.get( readSegment( memento ) );
                        break;
                    case STRING_ENCODING:
                        memento.setCharset( readCharset( memento ) );
                        break;
                    case DATA_STRING:
                        memento.getData().add( readString( memento ) );
                        break;
                    case DATA_INTEGER:
                        memento.getData().add( readInteger( memento ) );
                        break;
                    case END_OF_FRAME:
                        memento.getLine().setPositionByteBuffer( memento.getByteBuffer().position() );
                        return toMessage( commandType, runMode, memento );
                    default:
                        memento.getLine().setPositionByteBuffer( NO_POSITION );
                        arguments.dumpStreamText( "Unknown enum ("
                            + ForkedProcessEventType.class.getSimpleName()
                            + ") "
                            + segmentType );
                }
            }
        }
        catch ( MalformedFrameException e )
        {
            if ( e.hasValidPositions() )
            {
                int length = e.readTo() - e.readFrom();
                memento.getLine().write( memento.getByteBuffer(), e.readFrom(), length );
            }
        }
        catch ( RuntimeException e )
        {
            getArguments().dumpStreamException( e );
        }
        catch ( IOException e )
        {
            printRemainingStream( memento );
            throw e;
        }
        finally
        {
            memento.reset();
        }

        throw new MalformedChannelException();
    }

    @Nonnull
    @Override
    protected final byte[] getEncodedMagicNumber()
    {
        return MAGIC_NUMBER_FOR_COMMANDS_BYTES;
    }

    @Nonnull
    @Override
    protected SegmentType[] nextSegmentType( @Nonnull MasterProcessCommand commandType )
    {
        switch ( commandType )
        {
            case NOOP:
            case BYE_ACK:
            case SKIP_SINCE_NEXT_TEST:
            case TEST_SET_FINISHED:
                return COMMAND_WITHOUT_DATA;
            case RUN_CLASS:
            case SHUTDOWN:
                return COMMAND_WITH_ONE_STRING;
            default:
                throw new IllegalArgumentException( "Unknown enum " + commandType );
        }
    }

    @Nonnull
    @Override
    protected Command toMessage( @Nonnull MasterProcessCommand commandType, RunMode runMode, @Nonnull Memento memento )
        throws MalformedFrameException
    {
        switch ( commandType )
        {
            case NOOP:
                checkEventArguments( memento, 0 );
                return NOOP;
            case BYE_ACK:
                checkEventArguments( memento, 0 );
                return BYE_ACK;
            case SKIP_SINCE_NEXT_TEST:
                checkEventArguments( memento, 0 );
                return SKIP_SINCE_NEXT_TEST;
            case TEST_SET_FINISHED:
                checkEventArguments( memento, 0 );
                return TEST_SET_FINISHED;
            case RUN_CLASS:
                checkEventArguments( memento, 1 );
                return toRunClass( (String) memento.getData().get( 0 ) );
            case SHUTDOWN:
                checkEventArguments( memento, 1 );
                return toShutdown( Shutdown.parameterOf( (String) memento.getData().get( 0 ) ) );
            default:
                throw new IllegalArgumentException( "Missing a branch for the event type " + commandType );
        }
    }
}
