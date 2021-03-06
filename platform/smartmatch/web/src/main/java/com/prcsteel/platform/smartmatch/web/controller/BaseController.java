package com.prcsteel.platform.smartmatch.web.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.mysql.jdbc.CommunicationsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Component;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.ModelAndView;

import com.google.gson.Gson;
import com.prcsteel.platform.acl.model.enums.RoleAuthType;
import com.prcsteel.platform.acl.model.model.Organization;
import com.prcsteel.platform.acl.model.model.Permission;
import com.prcsteel.platform.acl.model.model.Role;
import com.prcsteel.platform.acl.model.model.User;
import com.prcsteel.platform.acl.service.OrganizationService;
import com.prcsteel.platform.acl.service.PermissionService;
import com.prcsteel.platform.acl.service.RoleService;
import com.prcsteel.platform.acl.service.UserService;
import com.prcsteel.platform.common.constants.Constant;
import com.prcsteel.platform.common.exception.BusinessException;
import com.prcsteel.platform.common.utils.PageData;
import com.prcsteel.platform.common.vo.Result;
import com.prcsteel.platform.smartmatch.web.support.ShiroVelocity;
import com.prcsteel.platform.smartmatch.web.support.WebAppContextUtil;

@Component
public class BaseController {
	private static final Logger logger = LoggerFactory.getLogger(BaseController.class);
	@Value("${logoutUrl}")
	private String logoutUrl;
	@Value("${acl.domain}")
	private String aclDomain;
	@Value("${account.domain}")
	private String accountDomain;
	@Value("${order.domain}")
	private String orderDomain;
	@Value("${core.domain}")
	private String coreDomain;
	@Value("${smartmatch.domain}")
	private String smartmatchDomain;
	@Value("${ass.domain}")
	private String assDomain;
	@Value("${kuandao.domain}")
	private String kuandaoDomain;
	@Resource
	protected UserService userService;
	@Resource
	protected RoleService roleService;
	@Resource
	protected PermissionService permissionService;
	@Resource
	protected OrganizationService organizationService;

	@ModelAttribute
	public void auto(ModelMap out) {
		setShowName(out);
		out.put(Constant.ACL_DOMAIN, aclDomain);
		out.put(Constant.ACCOUNT_DOMAIN, accountDomain);
		out.put(Constant.ORDER_DOMAIN, orderDomain);
		out.put(Constant.CORE_DOMAIN, coreDomain);
		out.put(Constant.SMARTMATCH_DOMAIN, smartmatchDomain);
		out.put(Constant.ASS_DOMAIN, assDomain);
		out.put(Constant.LOGOUT_URL, logoutUrl);
		out.put(Constant.SHIRO, new ShiroVelocity());
		out.put(Constant.KUANDAO_DOMAIN, kuandaoDomain);
	}

	private void setShowName(ModelMap out) {
		User user = getLoginUser();
		if (user != null && user.getId() != null && Constant.USER_STATUS.NORMAL.getValue() == user.getStatus()) {
			out.put(Constant.USER_NAME, user.getName());
			out.put(Constant.ORG_NAME, getOrganization().getName());
		}

	}

	@InitBinder
	protected void initBinder(WebDataBinder binder) throws ServletException {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		dateFormat.setLenient(false);
		binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));
	}

	protected User getLoginUser() {
		return WebAppContextUtil.getLoginUser();
	}

	protected Role getUserRole() {
		return WebAppContextUtil.getUserRole();
	}

	protected Organization getOrganization() {
		return WebAppContextUtil.getOrganization();
	}

	protected List<Permission> getUserPermissionList() {
		User user = getLoginUser();
		if (user != null) {
			return permissionService.findAllPermissionsForUser(user.getId(), user.getRoleId());
		} else {
			return new ArrayList<>();
		}
	}

	/**
	 * 获取当前用户所能查看用户数据的ID集合
	 *
	 * @return 返回null时，代表可以查看所有用户的数据
	 */
	protected List<Long> getUserIds() {

		List<Long> ids = new ArrayList<Long>();

		Role role = getUserRole();
		User user = getLoginUser();

		if (RoleAuthType.ALL.getValue() == role.getType()) { // 所有数据

			return null;
		} else if (RoleAuthType.SAME_LEVEL.getValue() == role.getType()) { // 同级＋下级

			List<Long> roles = roleService.queryRoleIds(role.getParentId());
			if (roles.size() > 0) {
				ids = userService.queryUserIdsByRoleIds(roles);
			}
			return ids;
		} else if (RoleAuthType.LOWER_LEVEL.getValue() == role.getType()) { // 下级
			List<Long> roles = roleService.queryRoleIds(role.getId());
			if (roles.size() > 0) {
				ids = userService.queryUserIdsByRoleIds(roles);
			}
			ids.add(user.getId());
			return ids;
		} else { // 仅自己
			ids.add(user.getId());
			return ids;
		}
	}

	protected void sendError(ModelMap model, String message) {
		model.addAttribute("error", message);
	}

	/**
	 * 得到request对象
	 */
	public HttpServletRequest getRequest() {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
				.getRequest();

		return request;
	}

	/**
	 * 得到ModelAndView
	 */
	public ModelAndView getModelAndView() {
		return new ModelAndView();
	}

	/**
	 * 得到PageData
	 */
	public PageData getPageData() {
		return new PageData(this.getRequest());
	}

	/** 基于@ExceptionHandler异常处理 */
	@ExceptionHandler
	public ModelAndView exp(HttpServletRequest request, HttpServletResponse response, Exception ex) {

		String errorMessage = null;
		String errorCode = null;
		// 业务类异常处理
		ModelAndView mv = new ModelAndView();
		// 数据库异常处理 add by zhoucai@prcsteel.com 2016-5-31
		if (ex instanceof CommunicationsException || ex instanceof SQLException || ex instanceof SocketException) {
			ModelMap out = mv.getModelMap();
			setShowName(out);
			mv.setViewName("timeOutError");
			return mv;
		}
		if (ex instanceof BusinessException) {
			BusinessException bus = (BusinessException) ex;
			errorCode = bus.getCode();
			errorMessage = bus.getMsg();
		} else {
			errorMessage = ex.getMessage();
		}
		// 已知其他异常处理
		logger.error(errorMessage, ex);
		String requestBy = request.getHeader(Constant.HTTP_HEAD_REQUEST_WITH);
		if (requestBy != null && requestBy.toLowerCase().equals(Constant.AJAX_REQUEST_HEAD_VALUE)) {
			Result res = new Result();
			res.setSuccess(false);
			res.setData(String.format("code:%s,message:%s", errorCode, errorMessage));
			Gson gson = new Gson();
			response.setCharacterEncoding("UTF-8");
			response.setContentType("application/json; charset=utf-8");
			byte[] byteRes = null;
			try {
				byteRes = gson.toJson(res).getBytes("utf-8");
				ServletOutputStream out = response.getOutputStream();
				out.write(byteRes, 0, byteRes.length);
				out.close();
			} catch (IOException e) {
				StringBuffer sb = new StringBuffer("write json result failed!    ");
				sb.append("req url  : [ ").append(request.getRequestURL()).append("    ]");
				sb.append("req params :   [");
				Enumeration em = request.getParameterNames();
				String paname = null;
				while (em.hasMoreElements()) {
					paname = (String) em.nextElement();
					sb.append(paname).append(":");
					sb.append(request.getParameter(paname));
				}
				sb.append("]         /");
				try {
					sb.append("rsp data [").append(new String(byteRes, "UTF-8")).append(" ] ");
				} catch (UnsupportedEncodingException e1) {
				}
				logger.error(sb.toString(), e);
			}
			return null;
		} else {
			ModelMap out = mv.getModelMap();
			setShowName(out);
			out.put("errorCode", errorCode);
			out.put("errorMessage", errorMessage);
			mv.setViewName("error");
		}
		return mv;
	}
}