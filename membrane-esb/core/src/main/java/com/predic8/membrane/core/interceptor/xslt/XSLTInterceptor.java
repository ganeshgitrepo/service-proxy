/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xslt;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stream.StreamSource;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;

public class XSLTInterceptor extends AbstractInterceptor {

	private String xslt;
	private int concurrency = 0; // number of parallel transformers to use. 0 = 2xCPUs
	private volatile XSLTTransformer xsltTransformer;
	private XOPReconstitutor xopr = new XOPReconstitutor();

	public XSLTInterceptor() {
		name = "XSLT Transformer";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		transformMsg(exc.getRequest(), xslt);
		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		transformMsg(exc.getResponse(), xslt);
		return Outcome.CONTINUE;
	}

	private void transformMsg(Message msg, String ss) throws Exception {
		if (msg.isBodyEmpty())
			return;
		msg.setBodyContent(getTransformer().transform(
				new StreamSource(xopr.reconstituteIfNecessary(msg))));
	}
	
	// http://en.wikipedia.org/wiki/Double-checked_locking
	private XSLTTransformer getTransformer() throws Exception {
		XSLTTransformer t = xsltTransformer;
		if (t == null) {
			synchronized(this) {
				t = xsltTransformer;
				if (t == null) {
					int concurrency = this.concurrency;
					if (concurrency <= 0)
						concurrency = Runtime.getRuntime().availableProcessors() * 2;
					xsltTransformer = t = new XSLTTransformer(xslt, router.getResourceResolver(), concurrency);
				}
			}
		}
		return t;
	}

	public String getXslt() {
		return xslt;
	}

	public void setXslt(String xslt) {
		this.xslt = xslt;
		this.xsltTransformer = null;
	}
	
	public int getConcurrency() {
		return concurrency;
	}
	
	public void setConcurrency(int concurrency) {
		this.concurrency = concurrency;
		this.xsltTransformer = null;
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		out.writeStartElement("transform");
		out.writeAttribute("xslt", xslt);
		if (concurrency != 0)
			out.writeAttribute("concurrency", ""+concurrency);
		out.writeEndElement();
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) {
		xslt = token.getAttributeValue("", "xslt");
		String concurrencyString = token.getAttributeValue("", "concurrency");
		concurrency = concurrencyString == null ? 0 : Integer.parseInt(concurrencyString);
	}

}