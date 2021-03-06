/*
 * Copyright Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags and
 * the COPYRIGHT.txt file distributed with this work.
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
package org.teiid.translator.couchbase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.resource.ResourceException;

import org.teiid.couchbase.CouchbaseConnection;
import org.teiid.language.QueryExpression;
import org.teiid.logging.LogConstants;
import org.teiid.logging.LogManager;
import org.teiid.metadata.RuntimeMetadata;
import org.teiid.translator.DataNotAvailableException;
import org.teiid.translator.ExecutionContext;
import org.teiid.translator.ResultSetExecution;
import org.teiid.translator.TranslatorException;

import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.query.N1qlQueryResult;
import com.couchbase.client.java.query.N1qlQueryRow;

public class CouchbaseQueryExecution extends CouchbaseExecution implements ResultSetExecution {
    
	private QueryExpression command;
	private Class<?>[] expectedTypes;
	
	private N1QLVisitor visitor;
	private Iterator<N1qlQueryRow> results;
	
	public CouchbaseQueryExecution(
			CouchbaseExecutionFactory executionFactory,
			QueryExpression command, ExecutionContext executionContext,
			RuntimeMetadata metadata, CouchbaseConnection connection) {
		super(executionFactory, executionContext, metadata, connection);
		this.command = command;
		this.expectedTypes = command.getColumnTypes();
	}

	@Override
	public void execute() throws TranslatorException {
		this.visitor = this.executionFactory.getN1QLVisitor();
		this.visitor.append(this.command);
		String n1ql = this.visitor.toString();
		LogManager.logDetail(LogConstants.CTX_CONNECTOR, CouchbasePlugin.Util.gs(CouchbasePlugin.Event.TEIID29001, n1ql));
		executionContext.logCommand(n1ql);
				
		N1qlQueryResult queryResult;
        try {
            queryResult = connection.execute(n1ql);
        } catch (ResourceException e) {
            throw new TranslatorException(e);
        }
		this.results = queryResult.iterator();
	}

	@Override
	public List<?> next() throws TranslatorException, DataNotAvailableException {
	    if (this.results == null || !this.results.hasNext()) {
	        return null;
	    }
        N1qlQueryRow queryRow = this.results.next();
        if(queryRow == null) {
            return null;
        }
        List<Object> row = new ArrayList<>(expectedTypes.length);
        JsonObject json = queryRow.value();
        
        for(int i = 0 ; i < expectedTypes.length ; i ++){
            String columnName = this.visitor.getSelectColumns().get(i);
            Object value = json.get(columnName);
            
            row.add(this.executionFactory.retrieveValue(expectedTypes[i], value));
        }
        return row;
	}

    @Override
	public void close() {
	    this.results = null;
	}

	@Override
	public void cancel() throws TranslatorException {
		close();
	}
}
