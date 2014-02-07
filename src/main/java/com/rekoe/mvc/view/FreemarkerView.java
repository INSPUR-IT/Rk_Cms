package com.rekoe.mvc.view;

import static com.rekoe.mvc.ProcessTimeProcessor.START_TIME;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.GenericServlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.nutz.lang.Files;
import org.nutz.lang.Lang;
import org.nutz.lang.Strings;
import org.nutz.mvc.Mvcs;
import org.nutz.mvc.view.AbstractPathView;
import org.nutz.web.Webs;

import com.rekoe.shiro.freemarker.ShiroTags;
import com.rekoe.web.freemarker.FreeMarkerConfigurer;

import freemarker.ext.jsp.TaglibFactory;
import freemarker.ext.servlet.HttpRequestHashModel;
import freemarker.ext.servlet.HttpRequestParametersHashModel;
import freemarker.ext.servlet.HttpSessionHashModel;
import freemarker.ext.servlet.ServletContextHashModel;
import freemarker.template.Configuration;
import freemarker.template.ObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateModel;

public class FreemarkerView extends AbstractPathView {

	private FreeMarkerConfigurer freeMarkerConfigurer;
	private static final String ATTR_APPLICATION_MODEL = ".freemarker.Application";
	private static final String ATTR_JSP_TAGLIBS_MODEL = ".freemarker.JspTaglibs";
	private static final String ATTR_REQUEST_MODEL = ".freemarker.Request";
	private static final String ATTR_REQUEST_PARAMETERS_MODEL = ".freemarker.RequestParameters";
	private static final String KEY_APPLICATION = "Application";
	private static final String KEY_REQUEST_MODEL = "Request";
	private static final String KEY_SESSION_MODEL = "Session";
	private static final String KEY_REQUEST_PARAMETER_MODEL = "Parameters";
	private static final String KEY_EXCEPTION = "exception";
	private static final String OBJ = "obj";
	private static final String REQUEST = "request";
	private static final String RESPONSE = "response";
	private static final String SESSION = "session";
	private static final String APPLICATION = "application";
	private static final String KEY_JSP_TAGLIBS = "JspTaglibs";
	public static final String PATH_BASE = "base";

	public FreemarkerView(FreeMarkerConfigurer freeMarkerConfigurer,String path) {
		super(path);
		this.freeMarkerConfigurer = freeMarkerConfigurer;
	}

	public void render(HttpServletRequest request, HttpServletResponse response, Object value) throws Throwable {
		String $temp = evalPath(request, value);
		String path = getPath($temp);
		ServletContext sc = request.getSession().getServletContext();
		//Ioc ioc = Mvcs.getIoc();
		//FreeMarkerConfigurer freeMarkerConfigurer = ioc.get(FreeMarkerConfigurer.class);
		Configuration cfg = freeMarkerConfigurer.getConfiguration();
		Map<String, Object> root = new HashMap<String, Object>();
		root.put(OBJ, value);
		root.put(REQUEST, request);
		root.put(RESPONSE, response);
		HttpSession session = request.getSession();
		root.put(SESSION, session);
		root.put(APPLICATION, sc);
		root.put(START_TIME, request.getAttribute(START_TIME));
		root.put(Webs.ME, session.getAttribute(Webs.ME));
		root.put("props", System.getProperties());// .get("java.version")
		Map<String, String> msgs = Mvcs.getMessages(request);
		root.put("mvcs", msgs);
		Enumeration<?> reqs = request.getAttributeNames();
		while (reqs.hasMoreElements()) {
			String strKey = (String) reqs.nextElement();
			root.put(strKey, request.getAttribute(strKey));
		}
		jspTaglibs(sc, request, response, root, cfg.getObjectWrapper());
		// cfg.setServletContextForTemplateLoading(request.getSession().getServletContext(),
		// "/");
		// 模版路径
		try {
			Template template = cfg.getTemplate(path);
			cfg.setSharedVariable("shiro", new ShiroTags());
			response.setContentType("text/html; charset=" + template.getEncoding());
			template.process(root, response.getWriter());
		} catch (Exception e) {
			throw Lang.wrapThrow(e);
		}
	}

	public String getWebRealPath(HttpServletRequest request) {
		StringBuffer sb = new StringBuffer();
		sb.append("http://");
		sb.append(request.getServerName());
		if (request.getServerPort() != 80) {
			sb.append(":");
			sb.append(request.getServerPort());
		}
		sb.append(request.getContextPath());
		sb.append("/");
		return sb.toString();
	}

	/**
	 * 子类可以覆盖这个方法，给出自己特殊的后缀
	 * 
	 * @return 后缀
	 */
	protected static String getExt() {
		return ".ftl";
	}

	private String getPath(String path) {
		StringBuffer sb = new StringBuffer();
		// 空路径，采用默认规则
		if (Strings.isBlank(path)) {
			sb.append(Mvcs.getServletContext().getRealPath("WEB-INF"));
			sb.append((path.startsWith("/") ? "" : "/"));
			sb.append(Files.renameSuffix(path, getExt()));
		}
		// 绝对路径 : 以 '/' 开头的路径不增加 '/WEB-INF'
		else if (path.charAt(0) == '/') {
			String ext = getExt();
			sb.append(path);
			if (!path.toLowerCase().endsWith(ext))
				sb.append(ext);
		}
		// 包名形式的路径
		else {
			sb.append(path.replace('.', '/'));
			sb.append(getExt());
		}
		return sb.toString();
	}

	protected void jspTaglibs(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response, Map<String, Object> model, ObjectWrapper wrapper) {
		synchronized (servletContext) {
			ServletContextHashModel servletContextModel = (ServletContextHashModel) servletContext.getAttribute(ATTR_APPLICATION_MODEL);
			if (Lang.isEmpty(servletContextModel)) {
				GenericServlet servlet = JspSupportServlet.jspSupportServlet;
				if (!Lang.isEmpty(servlet)) {
					servletContextModel = new ServletContextHashModel(servlet, wrapper);
					servletContext.setAttribute(ATTR_APPLICATION_MODEL, servletContextModel);
					TaglibFactory taglibs = new TaglibFactory(servletContext);
					servletContext.setAttribute(ATTR_JSP_TAGLIBS_MODEL, taglibs);
				}
			}
			model.put(KEY_APPLICATION, servletContextModel);
			TemplateModel tempModel = (TemplateModel) servletContext.getAttribute(ATTR_JSP_TAGLIBS_MODEL);
			model.put(KEY_JSP_TAGLIBS, tempModel);
		}
		HttpSession session = request.getSession(false);
		if (!Lang.isEmpty(session)) {
			model.put(KEY_SESSION_MODEL, new HttpSessionHashModel(session, wrapper));
		}
		HttpRequestHashModel requestModel = (HttpRequestHashModel) request.getAttribute(ATTR_REQUEST_MODEL);
		if (Lang.isEmpty(requestModel) || !Lang.equals(requestModel.getRequest(), request)) {
			requestModel = new HttpRequestHashModel(request, response, wrapper);
			request.setAttribute(ATTR_REQUEST_MODEL, requestModel);
		}
		model.put(KEY_REQUEST_MODEL, requestModel);
		HttpRequestParametersHashModel reqParametersModel = (HttpRequestParametersHashModel) request.getAttribute(ATTR_REQUEST_PARAMETERS_MODEL);
		if (Lang.isEmpty(reqParametersModel) || !Lang.equals(requestModel.getRequest(), request)) {
			reqParametersModel = new HttpRequestParametersHashModel(request);
			request.setAttribute(ATTR_REQUEST_PARAMETERS_MODEL, reqParametersModel);
		}
		model.put(KEY_REQUEST_PARAMETER_MODEL, reqParametersModel);
		Throwable exception = (Throwable) request.getAttribute("javax.servlet.error.exception");
		if (Lang.isEmpty(exception)) {
			exception = (Throwable) request.getAttribute("javax.servlet.error.JspException");
		}
		if (!Lang.isEmpty(exception)) {
			model.put(KEY_EXCEPTION, exception);
		}
	}
}