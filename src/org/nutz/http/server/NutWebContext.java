package org.nutz.http.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.nutz.http.impl.HttpObject;
import org.nutz.http.impl.Mimes;
import org.nutz.http.impl.NutHttpAction;
import org.nutz.http.impl.NutHttpReq;
import org.nutz.http.impl.NutHttpResp;
import org.nutz.lang.Files;
import org.nutz.lang.Lang;
import org.nutz.lang.Streams;
import org.nutz.log.Log;
import org.nutz.log.Logs;


public class NutWebContext extends HttpObject {
	
	private static final Log log = Logs.get();
	
	ExecutorService es = Executors.newFixedThreadPool(1024);
	
	List<NutHttpAction> actions = new ArrayList<NutHttpAction>();
	
	public int port;
	
	public boolean running = true;
	
	public void close() {
		running = false;
		es.shutdown();
	}
	
	public void workFor(final NutHttpReq req) {
		if (!running) {
			try {
				req.socket().close();
			} catch (IOException e1) {}
			return;
		}
			
		es.execute(new Runnable() {
			
			public void run() {
				NutHttpAction action = findAction(req);
				if (action == null)
					action = defaultHttpAction;
				try {
					log.debug("Work for req URI="+req.requestURI());
					action.exec(req, req.resp());
					log.debug("Done for req URI="+req.requestURI());
				} catch (Throwable e) {
					log.warn(e.getMessage(), e);
				} finally {
					try {
						req.socket().close();
					} catch (IOException e1) {}
				}
			}
		});
	}
	
	public NutHttpAction findAction(NutHttpReq req) {
		for (NutHttpAction action : actions) {
			if (action.canWork(req))
				return action;
		}
		return null;
	}
	
	protected String root = "root";
	
	protected NutHttpAction defaultHttpAction = new NutHttpAction() {
		
		public void exec(NutHttpReq req, NutHttpResp resp) {
			try {
				File f = new File(root + req.requestURI());
				if (f.exists() && f.isDirectory()) {
					f = new File(root + req.requestURI() + "/index.html");
				}
				if (f.exists() && f.isFile()) {
					resp.setContentLength((int)f.length());
					resp.setContentType(Mimes.guess(Files.getSuffixName(f)));
					resp.setDateHeader("Last-Modify", f.lastModified());
					resp.sendRespHeaders();
					Streams.write(resp.getOutputStream(), new FileInputStream(f));
				} else {
					resp.sendError(404, "File Not Found", null);
				}
			} catch (IOException e) {
				throw Lang.wrapThrow(e);
			}
		}
		
		public boolean canWork(NutHttpReq req) {
			return true;
		}
	};
}