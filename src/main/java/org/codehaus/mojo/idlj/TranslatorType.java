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

/**
 * A selector for the types of IDL translators supported
 */
enum TranslatorType
{
    DEFAULT
    {
        @Override
        boolean select( String compilerSetting )
        {
            return compilerSetting == null;
        }

        @Override
        CompilerTranslator createTranslator()
        {
            return isJavaModuleSystemPresent() ?  new GlassfishTranslator() : new BuiltInTranslator();
        }
    },
    BUILT_IN
    {
        @Override
        boolean select( String compilerSetting )
        {
            return compilerSetting.equals( "idlj" );
        }

        @Override
        CompilerTranslator createTranslator()
        {
            return new BuiltInTranslator();
        }
    },
    GLASSFISH
    {
        @Override
        boolean select( String compilerSetting )
        {
            return compilerSetting.equals( "glassfish" );
        }

        @Override
        CompilerTranslator createTranslator()
        {
            return new GlassfishTranslator();
        }
    },
    OPENORB
    {
        @Override
        boolean select( String compilerSetting )
        {
            return compilerSetting.equals( "openorb" );
        }

        @Override
        CompilerTranslator createTranslator()
        {
            return new OpenorbTranslator();
        }
    },
    JACORB
    {
        @Override
        boolean select( String compilerSetting )
        {
            return compilerSetting.equals( "jacorb" );
        }

        @Override
        CompilerTranslator createTranslator()
        {
            return new JacorbTranslator();
        }
    };

    private static boolean isJavaModuleSystemPresent()
    {
        return !System.getProperty( "java.version" ).startsWith( "1." );
    }

    static CompilerTranslator selectTranslator( String compiler ) throws MojoExecutionException
    {
        for ( TranslatorType type : TranslatorType.values() )
        {
            if ( type.select( compiler ) )
            {
                return type.createTranslator();
            }
        }

        throw new MojoExecutionException( "Compiler not supported: " + compiler );
    }

    abstract boolean select( String compilerSetting );

    abstract CompilerTranslator createTranslator();
}
