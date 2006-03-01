package org.apache.maven.surefire.report;

/*
 * Copyright 2001-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.NumberFormat;

/**
 * @author <a href="mailto:jason@maven.org">Jason van Zyl</a>
 * @version $Id$
 */
public abstract class AbstractReporter
    implements Reporter
{
    private String reportsDirectory;

    protected int completedCount;

    protected int errors;

    protected int failures;

    protected long startTime;

    protected long endTime;

    private NumberFormat numberFormat = NumberFormat.getInstance();

    protected static final String NL = System.getProperty( "line.separator" );

    private static final int MS_PER_SEC = 1000;

    protected long batteryStartTime;

    public void setReportsDirectory( String reportsDirectory )
    {
        this.reportsDirectory = reportsDirectory;
    }

    public String getReportsDirectory()
    {
        return reportsDirectory;
    }

    // ----------------------------------------------------------------------
    // Report interface
    // ----------------------------------------------------------------------

    public void runStarting( int testCount )
    {
    }

    public void runCompleted()
    {
    }

    public void runStopped()
    {
    }

    public void runAborted( ReportEntry report )
    {
    }

    public void batteryStarting( ReportEntry report )
        throws IOException
    {
        batteryStartTime = System.currentTimeMillis();
    }

    public void batteryCompleted( ReportEntry report )
    {
    }

    public void batteryAborted( ReportEntry report )
    {
    }

    // ----------------------------------------------------------------------
    // Test
    // ----------------------------------------------------------------------

    public void testStarting( ReportEntry report )
    {
        startTime = System.currentTimeMillis();
    }

    public void testSucceeded( ReportEntry report )
    {
        endTest();
    }

    public void testError( ReportEntry report, String stdOut, String stdErr )
    {
        ++errors;

        endTest();
    }

    public void testFailed( ReportEntry report, String stdOut, String stdErr )
    {
        ++failures;

        endTest();
    }

    private void endTest()
    {
        ++completedCount;

        endTime = System.currentTimeMillis();
    }

    // ----------------------------------------------------------------------
    // Counters
    // ----------------------------------------------------------------------

    public int getNbErrors()
    {
        return errors;
    }

    public int getNbFailures()
    {
        return failures;
    }

    public int getNbTests()
    {
        return completedCount;
    }

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    public void dispose()
    {
        errors = 0;

        failures = 0;

        completedCount = 0;
    }

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    protected String elapsedTimeAsString( long runTime )
    {
        return numberFormat.format( (double) runTime / MS_PER_SEC );
    }

    /**
     * Returns stacktrace as String.
     *
     * @param report ReportEntry object.
     * @return stacktrace as string.
     */
    protected static String getStackTrace( ReportEntry report )
    {
        StringWriter writer = new StringWriter();

        report.getThrowable().printStackTrace( new PrintWriter( writer ) );

        writer.flush();

        return writer.toString();
    }
}
