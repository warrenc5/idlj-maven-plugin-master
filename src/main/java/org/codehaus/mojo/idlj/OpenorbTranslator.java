package org.codehaus.mojo.idlj;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * This class implement the <code>CompilerTranslator</code> for the Openorb IDL compiler
 *
 * @author Anders Hessellund Jensen <ahj@trifork.com>
 * @version $Id$
 */
class OpenorbTranslator
        extends AbstractTranslator
        implements CompilerTranslator
{

    /**
     * Default constructor
     */
    OpenorbTranslator()
    {
        super();
    }

    /**
     * Invoke the specified compiler with a set of arguments
     *
     * @param compilerClass the <code>Class</code> that implements the compiler
     * @param args          a <code>List</code> that contains the arguments to use for the compiler
     * @throws MojoExecutionException if the compilation fail or the compiler crashes
     */
    private void invokeCompiler( Class<?> compilerClass, List<String> args )
            throws MojoExecutionException
    {
        // It would be great to use some 3rd party library for this stuff
        if ( !isFork() )
        {
            invokeCompilerInProcess( compilerClass, args );
        }
        else
        {

            // Forks a new java process.
            // Get path to java binary
            File javaHome = new File( System.getProperty( "java.home" ) );
            File javaBin = new File( new File( javaHome, "bin" ), "java" );

            // Get current class path
            URLClassLoader cl = (URLClassLoader) this.getClass().getClassLoader();
            URL[] classPathUrls = cl.getURLs();

            // Construct list of arguments
            List<String> binArgs = new ArrayList<>();

            // First argument is the java binary to run
            binArgs.add( javaBin.getPath() );

            // Add the classpath to argument list
            binArgs.add( "-classpath" );
            String classPath = "" + new File( classPathUrls[0].getPath().replaceAll( "%20", " " ) );
            for ( URL url : classPathUrls )
            {
                classPath += File.pathSeparator + new File( url.getPath().replaceAll( "%20", " " ) );
            }
            binArgs.add( classPath );

            // Add class containing main method to arg list
            binArgs.add( compilerClass.getName() );

            // Add java arguments
            for ( String arg : args )
            {
                binArgs.add( arg );
            }

            // Convert arg list to array
            String[] argArray = binArgs.toArray( new String[binArgs.size()] );

            if ( isDebug() )
            {
                getLog().debug( StringUtils.join( argArray, " " ) );
            }

            try
            {
                Process p = Runtime.getRuntime().exec( argArray );
                redirectStream( p.getErrorStream(), System.err );
                redirectStream( p.getInputStream(), System.out );

                p.waitFor();

                if ( isFailOnError() && p.exitValue() != 0 )
                {
                    throw new MojoExecutionException( "IDL Compilation failure" );
                }
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Error forking compiler", e );
            }
            catch ( InterruptedException e )
            {
                throw new MojoExecutionException( "Thread interrupted unexpectedly", e );
            }
        }
    }

    @Override
    protected int runCompiler( Class<?> compilerClass, String... arguments )
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException
    {
        Method compileMethod = compilerClass.getMethod( "compile", String[].class );
        compileMethod.invoke( compilerClass, new Object[]{arguments} );
        return 0;
    }

    /**
     * This method it's used to invoke the compiler
     *
     * @param sourceDirectory the path to the sources
     * @param includeDirs     the <code>File[]</code> of directories where to find the includes
     * @param targetDirectory the path to the destination of the compilation
     * @param idlFile         the path to the file to compile
     * @param source          the source set on which to run the compiler
     * @throws MojoExecutionException the exeception is thrown whenever the compilation fails or crashes
     */
    public void invokeCompiler( String sourceDirectory, File[] includeDirs, String targetDirectory, String idlFile,
                                Source source )
            throws MojoExecutionException
    {
        List<String> args = new ArrayList<>();

	if(super.isDebug())
          args.add( "-verbose");

        args.add( "-I" + sourceDirectory );

        // add idl files from other directories as well
        if ( includeDirs != null )
        {
            for ( File includeDir : includeDirs )
            {
                args.add( "-I" + includeDir.getPath() );
            }
        }

        args.add( "-d" );
        args.add( targetDirectory );

        if ( source.emitSkeletons() != null && !source.emitSkeletons() )
        {
            args.add( "-noskeleton" );
        }
        if ( source.emitStubs() != null && !source.emitStubs() )
        {
            args.add( "-nostub" );
        }

        if ( source.getPackagePrefix() != null )
        {
            args.add( "-package" );
            args.add( source.getPackagePrefix() );
        }

        if ( source.getPackagePrefixes() != null )
        {
            for ( PackagePrefix prefix : source.getPackagePrefixes() )
            {
              throw new UnsupportedOperationException("openorb doesn't support package prefixes");
            }
        }

        if ( source.getDefines() != null )
        {
            for ( Define define : source.getDefines() )
            {
                String arg = "-D" + define.getSymbol();
                if ( define.getValue() != null )
                {
                    arg += "=" + define.getValue();
                }
                args.add( arg );
            }
        }

        if ( source.getAdditionalArguments() != null )
        {
            for ( String addArg : source.getAdditionalArguments() )
            {
                args.add( addArg );
            }
        }

        args.add( idlFile );

        Class<?> compilerClass;
        try
        {
            compilerClass = getClassLoaderFacade().loadClass( "org.openorb.compiler.IdlCompiler" );
        }
        catch ( ClassNotFoundException e )
        {
            throw new MojoExecutionException( "OpenORB IDL compiler not found", e );
        }

        invokeCompiler( compilerClass, args );
    }

    /**
     * This methos it's used to redirect an <code>InputeStream</code> to a <code>OutputStream</code>
     *
     * @param in  the <code>InputStream</code> to read from
     * @param out the <code>OutputStream</code> to write into
     */
    private static void redirectStream( final InputStream in, final OutputStream out )
    {
        Thread stdoutTransferThread = new Thread()
        {
            public void run()
            {
                PrintWriter pw = new PrintWriter( new OutputStreamWriter( out ), true );
                try
                {
                    BufferedReader reader = new BufferedReader( new InputStreamReader( in ) );
                    String line;
                    while ( ( line = reader.readLine() ) != null )
                    {
                        pw.println( line );
                    }
                }
                catch ( Throwable e )
                {
                    e.printStackTrace();
                }
            }
        };
        stdoutTransferThread.start();
    }
}
/**
 * Usage: java org.openorb.compiler.IdlCompiler [Options] idl-files...
Options:
--------
  -all
              Generate mapping for included files.
  -boa
              Generate skeleton for the BOA approach.
  -d <directory_name>
              Provide a way to specify the ouput dir. This option
              will not use the 'generated' directory.
              Example:
                  org.openorb.compiler.IdlCompiler -d /home/me/ demo.idl
  -dynamic    Generate stub with DII and skeleton with DSI
              ( portable way before CORBA 2.3 ).
  -D <symbol>
              Define a symbol. It is equivalent to #define.
  -importLink <link>
              ???
  -I <include_folder
              Allow specification of include directory.
              Example:
                  org.openorb.compiler.IdlCompiler -I /home/me/idl demo.idl
  -native <native_name> <native_mapping>
              Define native type mapping.
              Example:
                  org.openorb.compiler.IdlCompiler
                      -native cookie java.lang.Object demo.idl
                  This command implies the mapping of cookie into
                  java.lang.Object.
  -nolocalstub
              Generate stubs without local invocation path.
  -noprefix
              Don't use prefixes as packages names.
  -noreverseprefix
              The prefixes are used as package name but they are not reversed.
  -noskeleton
              Don't generate skeleton.
  -nostub
              Don't generate stub.
  -notie
              Don't generate TIE classes for delegation mode.
  -package <package_name>
              Generate files in package_name.
              Example:
              org.openorb.compiler.IdlCompiler -package example demo.idl
  -pidl
              ???
  -portablehelper
              Generate portable helper classes. Nonportable helpers must be
              compiled using OpenORB, but the generated class files will work
              anywhere. Portable helpers can be compiled anywhere and will
              work identically on all orbs with Any.extract_Streamable.
  -quiet
              Suppress any output. Same as -silence.
  -silence
              Suppress any output. Same as -quiet.
  -verbose
              Show debug output.
  -jdk1.4
              Generate classes that use JDK1.4 features.
              The generate classes will not compile on previous versions.
  -invokeMethod <Classes|Reflection|Switch>
              The method used to implement the xxxPOA invoke method.
              If not specified Classes is used.
  -minTableSize <size>
              The minimum size of method table.
  -retainPossibleCause
              Add extra instrumentation to generated classes to ensure that
              possible causes are retained. Debugging option: non portable.
  -XgenerateValueFactory <impl_postfix>
              Generate default value factories for valuetypes.
              Creates instance of <valuetype name><impl postfix>.
  -XgenerateValueImpl <postfix>
              Generatae default implementation of valuetypes.
              Creates classes with name <valuetype name><postfix>.
              
*/
