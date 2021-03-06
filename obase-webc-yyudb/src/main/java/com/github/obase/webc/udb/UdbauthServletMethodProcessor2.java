package com.github.obase.webc.udb;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;

import com.github.obase.kit.StringKit;
import com.github.obase.security.Principal;
import com.github.obase.webc.AuthType;
import com.github.obase.webc.Kits;
import com.github.obase.webc.ServletMethodHandler;
import com.github.obase.webc.ServletMethodObject;
import com.github.obase.webc.Webc;
import com.github.obase.webc.Wsid;
import com.github.obase.webc.config.WebcConfig.FilterInitParam;
import com.github.obase.webc.support.security.WsidServletMethodProcessor;
import com.github.obase.webc.udb.UdbKit.Callback;
import com.github.obase.webc.yy.UserPrincipal;

public abstract class UdbauthServletMethodProcessor2 extends WsidServletMethodProcessor implements Callback {

	protected abstract String getAppid();

	protected abstract String getAppkey();

	protected abstract String getHomepage();

	protected abstract String getLogoutpage();

	@Override
	public void setup(FilterInitParam params, Map<String, Map<HttpMethod, ServletMethodObject>> rules) throws ServletException {

		ServletMethodHandler loginObject = new ServletMethodHandler() {
			@Override
			public void service(HttpServletRequest request, HttpServletResponse response) throws Exception {
				UdbKit.login(request, response, Kits.getNamespace(request));
			}
		};

		ServletMethodHandler genUrlTokenObject = new ServletMethodHandler() {
			@Override
			public void service(HttpServletRequest request, HttpServletResponse response) throws Exception {
				UdbKit.genUrlToken(request, response, Kits.getNamespace(request), getAppid(), getAppkey());
			}
		};

		ServletMethodHandler callbackObject = new ServletMethodHandler() {
			@Override
			public void service(HttpServletRequest request, HttpServletResponse response) throws Exception {
				UdbKit.callback(request, response, Kits.getNamespace(request), getAppid(), getAppkey(), getHomepage(), UdbauthServletMethodProcessor2.this);
			}
		};

		ServletMethodHandler denyCallbackObject = new ServletMethodHandler() {
			@Override
			public void service(HttpServletRequest request, HttpServletResponse response) throws Exception {
				UdbKit.denyCallback(request, response);
			}
		};

		ServletMethodHandler logoutObject = new ServletMethodHandler() {
			@Override
			public void service(HttpServletRequest request, HttpServletResponse response) throws Exception {
				UdbKit.logout(request, response, Kits.getNamespace(request), getAppid(), getAppkey(), getLogoutpage(), UdbauthServletMethodProcessor2.this);
			}
		};

		rules.put(UdbKit.LOOKUP_PATH_LOGIN, fillObject(new ServletMethodObject(UdbKit.LOOKUP_PATH_LOGIN, loginObject, null, AuthType.NONE)));
		rules.put(UdbKit.LOOKUP_PATH_GEN_URL_TOKEN, fillObject(new ServletMethodObject(UdbKit.LOOKUP_PATH_GEN_URL_TOKEN, genUrlTokenObject, null, AuthType.NONE)));
		rules.put(UdbKit.LOOKUP_PATH_CALLBACK, fillObject(new ServletMethodObject(UdbKit.LOOKUP_PATH_CALLBACK, callbackObject, null, AuthType.NONE)));
		rules.put(UdbKit.LOOKUP_PATH_DENY_CALLBACK, fillObject(new ServletMethodObject(UdbKit.LOOKUP_PATH_DENY_CALLBACK, denyCallbackObject, null, AuthType.NONE)));
		rules.put(UdbKit.LOOKUP_PATH_LOGOUT, fillObject(new ServletMethodObject(UdbKit.LOOKUP_PATH_LOGOUT, logoutObject, null, AuthType.NONE)));

		super.setup(params, rules);
	}

	@Override
	public boolean postUdbLogin(HttpServletRequest request, HttpServletResponse response, String yyuid, String[] uProfile) throws IOException {

		Principal principal = validatePrincipal(yyuid, uProfile);
		if (principal == null) {
			return false;
		}

		Wsid wsid = Wsid.valueOf(principal.getKey()).resetToken(wsidTokenBase); // csrf
		getWsidSession().passivate(wsid.id, encodePrincipal(principal), timeoutMillis);

		request.setAttribute(Webc.ATTR_WSID, wsid);
		request.setAttribute(Webc.ATTR_PRINCIPAL, principal);
		Kits.writeCookie(response, wsidName, Wsid.encode(wsid), wsidDomain, Wsid.COOKIE_PATH, Wsid.COOKIE_TEMPORY_EXPIRE);

		return true;
	}

	@Override
	public void preUdbLogout(HttpServletRequest request, HttpServletResponse response) {

		Wsid wsid = Kits.getWsid(request);
		if (wsid == null) {
			wsid = Wsid.decode(Kits.readCookie(request, wsidName));
		}
		if (wsid != null) {
			getWsidSession().activate(wsid.id, 0);
		}

		Kits.writeCookie(response, wsidName, "", wsidDomain, Wsid.COOKIE_PATH, 0);
	}

	// for subclass override
	protected Principal validatePrincipal(String yyuid, String[] uProfile) {
		UserPrincipal principal = new UserPrincipal();
		principal.setYyuid(yyuid);
		principal.setPassport(uProfile[0]);
		principal.setRealname(uProfile[0]);
		return principal;
	}

	@Override
	protected void redirectLoginPage(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		StringBuilder psb = new StringBuilder(256);
		psb.append(request.getContextPath()).append(request.getServletPath());
		String queryString = request.getQueryString();
		if (StringKit.isNotEmpty(queryString)) {
			psb.append('?').append(queryString);
		}

		StringBuilder sb = new StringBuilder(256);
		sb.append(Kits.getServletPath(request, UdbKit.LOOKUP_PATH_LOGIN));
		sb.append("?").append(UdbKit.PARAM_URL).append("=").append(URLEncoder.encode(psb.toString(), Webc.CHARSET_NAME));

		Kits.sendRedirect(response, sb.toString());
	}

	@Override
	protected String encodePrincipal(Principal p) {
		return ((UserPrincipal) p).encode();
	}

	@Override
	protected Principal decodePrincipal(String v) {
		return v == null ? null : new UserPrincipal().decode(v);
	}

}
