package com.hl.web.controller;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.xml.rpc.ServiceException;
import javax.xml.rpc.encoding.XMLType;
import javax.xml.soap.SOAPException;



import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;
import org.aspectj.weaver.EnumAnnotationValue;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jdom.JDOMException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.hibernate3.HibernateTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.google.common.collect.Maps;
import com.hl.alipay.config.AlipayConfig;
import com.hl.alipay.util.AlipayNotify;
import com.hl.alipay.util.AlipaySubmit;
import com.hl.common.model.RespInfo;
import com.hl.common.util.StringUtils;
import com.hl.common.web.BaseController;
import com.hl.common.web.JsonModelAndView;
import com.hl.web.entity.ChargeAccountCardRecord;
import com.hl.web.entity.CompanyInfo;
import com.hl.web.entity.ProductionOrdering;
import com.hl.web.entity.ProductionOrdering2;
import com.hl.web.form.AbstractInfoForm;
import com.hl.web.form.CustomerForm;
import com.hl.web.form.CustomerTotalPayInfoForm;
import com.hl.web.form.HistoryAbDetailForm;
import com.hl.web.form.HistoryPayInfoForm;
import com.hl.web.form.OrderForm;
import com.hl.web.form.PayInfoForm;
import com.hl.web.bank.UnionBase;
//import com.unionpay.acp.sdk.LogUtil;
import com.hl.web.sdk.SDKConfig;
import com.hl.web.service.PayThirdService;
import com.hl.web.service.UserService;
import com.hl.web.util.MmToolkit;
import com.hl.web.util.NetworkUtil;
import com.hl.web.util.RpcServletTool;
import com.hl.wsdl.node.Hl6WsdlService;
import com.hl.wsdl.node.ThirdPartyService;
import com.hl.wxin.common.Signature;
import com.hl.wxin.common.Util;
import com.hl.wxin.protocol.pay_protocol.ScanPayReqData;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.XmlFriendlyNameCoder;

@Controller
@RequestMapping("/payment")
public class PayManager extends BaseController{
	
	
	private Logger logger = Logger.getLogger(PayManager.class);
	
	@Autowired
	PayThirdService payservice;
	
	@Autowired
	private UserService userService;
	
	@RequestMapping("/getqr_Url.do")
	public JsonModelAndView getqr_Url(HttpServletRequest request,
			HttpServletResponse response,Model model) throws Exception
	{
		RespInfo resp = new RespInfo(0, "建立成功", null);
		
        String order_id = NetworkUtil.getFromBASE64(request.getParameter("order_id"));

        String total_fee = request.getParameter("amount");
		
		CompanyInfo companyInfo = payservice.getComInfoByOrderID(order_id);
        
        HashMap<String, String> paramMap = Maps.newHashMap(); 
        
        paramMap.put("trade_type", "NATIVE"); //交易类型
//		paramMap.put("spbill_create_ip", "192.168.0.1"); // 本机的Ip
		paramMap.put("spbill_create_ip", getIp(request));
        paramMap.put("product_id", "gas_pay"); // 商户根据自己业务传递的参数 必填
		paramMap.put("body", companyInfo.getCompanyName()+"燃气费支付"); // 描述
		paramMap.put("out_trade_no", order_id); // 商户 后台的贸易单号
		paramMap.put("total_fee", total_fee); // 金额必须为整数 单位为分
		paramMap.put("notify_url", "http://www.hl-epay.com"
				+ "/payment/wx_pay_notify.do"); // 支付成功后，回调地址
		paramMap.put("appid", companyInfo.getWx_appid()); // appid
		paramMap.put("mch_id", companyInfo.getWx_mch_num()); // 商户号
		paramMap.put("nonce_str", Util.CreateNoncestr()); // 随机数
		paramMap.put("sign", Signature.getSign(paramMap));// 根据微信签名规则，生成签名
		
		String postDataXML = MmToolkit.ArrayToXml(paramMap);
		
		logger.info(postDataXML);
		
        String resXml = RpcServletTool.postData("https://api.mch.weixin.qq.com/pay/unifiedorder", postDataXML,null);
        
        logger.info(resXml);
        
        Document dd = null;
        
        String code_url=null;
        
        try {
            dd = DocumentHelper.parseText(resXml);
          } catch (DocumentException e) {
               resp.setCode(-1);
               resp.setMessage("二维码URL解析失败");
               return new JsonModelAndView(resp);
        }
        if (dd != null) {
            Element root = dd.getRootElement();
            if (root == null) {
            	 resp.setCode(-1);
                 resp.setMessage("返回数据结构异常");
                 return new JsonModelAndView(resp);
            }
            Element codeUrl = root.element("code_url");
            if (codeUrl == null) {
            	resp.setCode(-1);
                resp.setMessage("无返回二维码结构code_url参数 ");
                return new JsonModelAndView(resp);
            }  
            code_url = codeUrl.getText();  //解析 xml 获得 code_url
        }
        
        resp.setData(code_url);
        
		return new JsonModelAndView(resp);
	}
	
	@RequestMapping("/genportalSNumber.do")
	public JsonModelAndView genportalSNumber(HttpServletRequest request,
			HttpServletResponse response) throws Exception
	{
        RespInfo resp = new RespInfo(0, "建立成功", null);
		
        String order_serial_detailStr = request.getParameter("orderForm");//明细
		
        order_serial_detailStr = "[{\"addressID\":\"cf648b9d-b700-4cc6-a761-ceff31449913\",\"meterSerialNo\":\"1\",\"addressDetail\":\"阳光维多利亚阳光。维多利亚3-1-13-3\",\"currentMeterFee\":\"0.00\",\"userTotalChargeFee\":\"12\",\"meterType\":\"PB\"}]";
        
		String totalMoney = request.getParameter("totalMoney");//总金额
		
		Double totalMoney_Double = Double.valueOf(totalMoney);
		
	    String companyBM = request.getParameter("companyCode");//公司
		
	    String customerInfo = request.getParameter("customerCode");//客户信息
	    
	    order_serial_detailStr = order_serial_detailStr.replaceAll("\\\\","");
	    	  
		String order_id = payservice.generateOrderingInfo(order_serial_detailStr,totalMoney_Double,companyBM,customerInfo);
	    
	    logger.info("生成订单号:"+order_id);
        
	    String order_num = order_id.substring(0, order_id.indexOf("|"));
	    
	    String txtTime = order_id.substring(order_id.indexOf("|")+1);
	    
	    System.out.println(order_num+":"+txtTime);
	    
	    List<String> list =new ArrayList<String>();

	    list.add(order_num);
	    
	    list.add(txtTime);
	    
		resp.setData(list);
		
		return new JsonModelAndView(resp);
	}
	
	
	@RequestMapping("/getPortalqrUrl.do")
	public JsonModelAndView getPortalqrUrl(HttpServletRequest request,
			HttpServletResponse response,Model model) throws Exception
	{
		RespInfo resp = new RespInfo(0, "建立成功", null);
		
        String order_id = request.getParameter("order_id");

        String total_fee = request.getParameter("amount");
		
		CompanyInfo companyInfo = payservice.getComInfoByOrderID(order_id);
        
        HashMap<String, String> paramMap = Maps.newHashMap(); 
        
        paramMap.put("trade_type", "NATIVE"); //交易类型
//		paramMap.put("spbill_create_ip", "192.168.0.1"); // 本机的Ip
		paramMap.put("spbill_create_ip", getIp(request));
        paramMap.put("product_id", "gas_pay"); // 商户根据自己业务传递的参数 必填
		paramMap.put("body", companyInfo.getCompanyName()+"燃气费支付"); // 描述
		paramMap.put("out_trade_no", order_id); // 商户 后台的贸易单号
		paramMap.put("total_fee", total_fee); // 金额必须为整数 单位为分
		paramMap.put("notify_url", "http://www.hl-epay.com"
				+ "/payment/wxatm_pay_notify.do"); // 支付成功后，回调地址
		paramMap.put("appid", companyInfo.getWx_appid()); // appid
		paramMap.put("mch_id", companyInfo.getWx_mch_num()); // 商户号
		paramMap.put("nonce_str", Util.CreateNoncestr()); // 随机数
		paramMap.put("sign", Signature.getSign(paramMap));// 根据微信签名规则，生成签名
		
		String postDataXML = MmToolkit.ArrayToXml(paramMap);
		
		logger.info(postDataXML);
		
        String resXml = RpcServletTool.postData("https://api.mch.weixin.qq.com/pay/unifiedorder", postDataXML,null);
        
        logger.info(resXml);
        
        Document dd = null;
        
        String code_url=null;
        
        try {
            dd = DocumentHelper.parseText(resXml);
          } catch (DocumentException e) {
               resp.setCode(-1);
               resp.setMessage("二维码URL解析失败");
               return new JsonModelAndView(resp);
        }
        if (dd != null) {
            Element root = dd.getRootElement();
            if (root == null) {
            	 resp.setCode(-1);
                 resp.setMessage("返回数据结构异常");
                 return new JsonModelAndView(resp);
            }
            Element codeUrl = root.element("code_url");
            if (codeUrl == null) {
            	resp.setCode(-1);
                resp.setMessage("无返回二维码结构code_url参数 ");
                return new JsonModelAndView(resp);
            }  
            code_url = codeUrl.getText();  //解析 xml 获得 code_url
        }
        
        resp.setData(code_url);
        
		return new JsonModelAndView(resp);
	}
	

	@RequestMapping("/isOrNotPaySuccessed.do")
	public JsonModelAndView isOrNotPaySuccessed(HttpServletRequest request,
			HttpServletResponse response,Model model) throws Exception
	{	
			RespInfo resp = new RespInfo(0, "建立成功", null);

            String order_id = request.getParameter("orderID");
				
            boolean isPaySuccessed = payservice.isPayStatusSuccessed(order_id);
                
            if(!isPaySuccessed)
            {
              resp.setCode(-1);
              resp.setMessage("支付未成功");
            }
				
	      return new JsonModelAndView(resp);
			
	}

	
	/**
	 * 加载客户信息
	 * @param request
	 * @param response
	 * @param model
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/clientinfo.do")
	public JsonModelAndView clientinfo(HttpServletRequest request,
			HttpServletResponse response,Model model) throws Exception
	{	
		
		RespInfo resp = new RespInfo(0, "建立成功", null);
		
		try{
			
		List<String> list = new ArrayList<String>();
		
		list.add("测试1");
		
		list.add("测试2");
		
		resp.setData(list);
		
		}
		catch(Exception e)
		{
			throw e;
		}

		return new JsonModelAndView(resp);
		
	}
	
	

	/**
	 * 载入cus信息
	 * @param request
	 * @param response
	 * @param model
	 * @return
	 * @throws UnsupportedEncodingException   ,method=RequestMethod.POST
	 */
	@RequestMapping(value="/firstPage.do")
	public String turnPageFirst(HttpServletRequest request,
			HttpServletResponse response,Model model) throws UnsupportedEncodingException
	{
		
		
		String payInfo = request.getParameter("customerInfo");//相关信息一并加载
		
		System.out.println("customerInfo~~~~~:::::"+payInfo);
		
        JSONObject jsonobj = JSONObject.fromObject(payInfo);
		
        Map<String, Class> classMap = new HashMap<String, Class>();  
        
        classMap.put("customer", CustomerForm.class);
        classMap.put("historyAbstractDetail", HistoryAbDetailForm.class);
        classMap.put("historyOne", HistoryPayInfoForm.class);
        classMap.put("historyThree", HistoryPayInfoForm.class);
        classMap.put("payInfo", PayInfoForm.class);
         
        CustomerTotalPayInfoForm customerForm = (CustomerTotalPayInfoForm)JSONObject.toBean(jsonobj,CustomerTotalPayInfoForm.class,classMap);
		
        
		model.addAttribute("customerName", customerForm.getCustomer().get(0).getYhname());
		model.addAttribute("customerCode",customerForm.getCustomer().get(0).getYhcode());
		
		System.out.println("companyName:" + customerForm.getCompanyName());
		
		model.addAttribute("companyName",customerForm.getCompanyName());
//		System.out.println(customerForm.getCompanyName()+"~~~~~~gonggogngogngn");
		model.addAttribute("companyCode",customerForm.getCompanyCode());
		model.addAttribute("abstractMoney",customerForm.getCustomer().get(0).getAbstractTotalMoney());
		model.addAttribute("bcye",customerForm.getCustomer().get(0).getBcye());
		
		JSONArray jsonArray = JSONArray.fromObject(customerForm.getHistoryAbstractDetail());  
	    String tmp = jsonArray.toString();
		model.addAttribute("historyAbDetail",tmp);//历史欠费信息
		jsonArray = JSONArray.fromObject(customerForm.getHistoryOne()); 
		model.addAttribute("historyOne",jsonArray.toString().trim());//历史一月缴费信息
		jsonArray = JSONArray.fromObject(customerForm.getHistoryThree());
		model.addAttribute("historyThree",jsonArray.toString().trim());
		jsonArray = JSONArray.fromObject(customerForm.getPayInfo());
		model.addAttribute("payInfo",jsonArray.toString());
		return "pay-check-first";
	}
	
	
	/**
	 * first页面校验跳转
	 * @param request
	 * @param response
	 * @return
	 * @throws IOException 
	 */
	@RequestMapping(value="/check")
	public JsonModelAndView checkCodeFun(HttpServletRequest request,
			HttpServletResponse response) throws IOException
	{
		RespInfo resp = new RespInfo(0, "建立成功", null);
		
		String str = request.getSession().getAttribute("capchacode").toString();
		
		if(!str.equals(request.getParameter("captchaInput").toUpperCase())){
			resp.setCode(-1);
			resp.setMessage("验证码错误");
			return new JsonModelAndView(resp);
		}
		
		
		return new JsonModelAndView(resp);
	}
	
	
	private boolean isCommonMeterPayment(String paydetail)
	{
		
		org.json.JSONArray jsonArray = new org.json.JSONArray(paydetail);
	    
	    for(int i=0;i<jsonArray.length();i++){

	    	org.json.JSONObject obj = jsonArray.getJSONObject(i);
	    	
	    	if(obj.getString("meterType").equals("PB"))
	    	{
	    		if(!MmToolkit.isDouble((obj.getString("userTotalChargeFee").trim()))?true:false)return false;
	    	}
	    	continue;
	    }
	    return true;
	}
	
	
	/**
	 * second页面校验跳转
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception 
	 */
	@RequestMapping("/checkSecond.do")
	public JsonModelAndView checkSecondFun(HttpServletRequest request,
			HttpServletResponse response) throws Exception
	{
        RespInfo resp = new RespInfo(0, "建立成功", null);
		
		/**
		 * 验证码验证 start
		 */

		String str = StringUtils.obj2Str(request.getSession(false).getAttribute("capchacode"));
		
		if(str==null)System.out.println("checkSecond session失效");
		
		Properties property =new Properties();

		property.load(new ByteArrayInputStream(StringUtils.obj2Str(request.getParameter("check_code")).getBytes("UTF-8")));
		
		if ("".equals(String.valueOf(property.get("captchaInput")).trim())) {
			return new JsonModelAndView(new RespInfo(-1, "请填入校验码", null));
		}
		
        
		if(!str.equals(String.valueOf(property.get("captchaInput")).trim().toUpperCase())){
			resp.setCode(-1);
			resp.setMessage("验证码错误");
			return new JsonModelAndView(resp);
		}
		
        String order_serial_detailStr = request.getParameter("orderForm");//明细
		
        
        if(!isCommonMeterPayment(order_serial_detailStr))
		{
			resp.setCode(-2);
			resp.setMessage("基表类型气表必须填入充值金额!");
			return new JsonModelAndView(resp);
		}
        //end

		String totalMoney = request.getParameter("totalMoney");//总金额
		
		Double totalMoney_Double = Double.valueOf(totalMoney);
		
	    String companyBM = request.getParameter("companyCode");//公司
		
	    String customerInfo = request.getParameter("customerCode");//客户信息
	    
	    order_serial_detailStr = order_serial_detailStr.replaceAll("\\\\","");
	    	  
		String order_id = payservice.generateOrderingInfo(order_serial_detailStr,totalMoney_Double,companyBM,customerInfo);
	    
	    logger.info("生成订单号:"+order_id);
        
	    String order_num = order_id.substring(0, order_id.indexOf("|"));
	    
	    String txtTime = order_id.substring(order_id.indexOf("|")+1);
	    
	    System.out.println(order_num+":"+txtTime);
	    
	    List<String> list =new ArrayList<String>();

	    list.add(NetworkUtil.getBASE64(order_num));
	    
	    list.add(txtTime);
	    
		resp.setData(list);
		
		return new JsonModelAndView(resp);
	}
	
	
	
	/**
	 * 跳转充值明细页面
	 * @param request
	 * @param response
	 * @param model
	 * @return
	 */
	@RequestMapping("/secondConfirm.do")
	public String turnPageSecond(HttpServletRequest request,
			HttpServletResponse response,Model model)
	{
		List a = new ArrayList();
		
		model.addAttribute("company",a);
		
		PayInfoForm payInfo = new PayInfoForm();
		
		model.addAttribute("customer","customerid");
		
		return "pay-infoconfirm-second";
	}
		
	
	
	/**
	 * 
	 * @param request
	 * @param response
	 * @return
	 * @throws ServiceException 
	 * @throws MalformedURLException 
	 * @throws RemoteException 
	 * @throws SOAPException 
	 */
	@RequestMapping("/authorize.do")
	public JsonModelAndView authUserInfo(HttpServletRequest request,
			HttpServletResponse response,Model model) throws RemoteException, MalformedURLException, ServiceException, SOAPException
	{
		
		RespInfo resp = new RespInfo(0, "建立成功", null);

		String code = request.getParameter("companyCode");
		
		String clientNumber = request.getParameter("clientNumber");
		
		CompanyInfo companyInfo = payservice.getComInfoByCode(code);
		
		if(companyInfo==null){
			resp.setCode(-1);
			resp.setMessage("公司编码不存在，请联系工作人员");
			return new JsonModelAndView(resp);
		}
		
		Map<String, String> params = new HashMap<String, String>();
		
		params.put("customerid", clientNumber);
		
        String payInfo = (String)RpcServletTool.callAsmxWebService(
        		RpcServletTool.combineQrCustomerInfoURL(companyInfo.getCloudIp(),companyInfo.getPort()),
                "http://tempuri.org/", "QueryCustomerInfoById", params,XMLType.XSD_STRING);

		/**
		 * 测试数据
		 */
//		String payInfo = "{\"customer\":[{\"adr\":\"阳光。维多利亚3-1-13-3\",\"yhname\":\"熊淑文 郭天珍\",\"yhcode\":\"03301040\",\"LandPhone\":\"15298015712\",\"abstractTotalMoney\":\"-12.00\",\"bcye\":\"0.00\"}],\"historyAbstractDetail\":[],\"historyOne\":[],\"historyThree\":[{\"createTime\":\"2016/03/11/18:33:02\",\"payFee\":\"45.08元\",\"payVolume\":\"1929.01\"}],\"payInfo\":[{\"addressID\":\"cf648b9d-b700-4cc6-a761-ceff31449913\",\"meterSerialNo\":\"1\",\"meterType\":\"PB\",\"meterName\":\"基表\",\"addressDetail\":\"阳光维多利亚阳光。维多利亚3-1-13-3\",\"currentMeterFee\":\"0.00\"},{\"addressID\":\"cf648b9d-b700-4cc6-a761-ceff31449913\",\"meterSerialNo\":\"1\",\"meterType\":\"YC\",\"meterName\":\"纯远传表\",\"addressDetail\":\"阳光维多利亚阳光。维多利亚3-1-13-3\",\"currentMeterFee\":\"0.00\"}]}";		
		
//		if(clientNumber.equals("10086"))
//		payInfo = "{\"customer\":[{\"adr\":\"大源社区二期3-1-13-3\",\"yhname\":\"李丽萍\",\"yhcode\":\"03301041\",\"LandPhone\":\"15298015712\",\"abstractTotalMoney\":\"-21.02\",\"bcye\":\"0.00\"}],\"historyAbstractDetail\":[{\"month\":\"201607\",\"abstractFee\":\"21.02\",\"addressDetail\":\"大源社区20栋30-01-02\",\"beginVolume\":\"1264\",\"endVolume\":\"2306\"}],\"historyOne\":[{\"createTime\":\"2016/07/11/18:33:02\",\"payFee\":\"28.18元\",\"payVolume\":\"2008.3\"}],\"historyThree\":[{\"createTime\":\"2016/05/11/18:33:02\",\"payFee\":\"45.08元\",\"payVolume\":\"1929.01\"},{\"createTime\":\"2016/07/11/18:33:02\",\"payFee\":\"28.18元\",\"payVolume\":\"2008.3\"}],\"payInfo\":[{\"addressID\":\"cf648b9d-b700-4cc6-a761-ceff31449913\",\"meterSerialNo\":\"1\",\"meterType\":\"PB\",\"meterName\":\"基表\",\"addressDetail\":\"阳光维多利亚阳光。维多利亚3-1-13-3\",\"currentMeterFee\":\"0.00\"},{\"addressID\":\"cf648b9d-b700-4cc6-a761-ceff31449913\",\"meterSerialNo\":\"1\",\"meterType\":\"YC\",\"meterName\":\"纯远传表\",\"addressDetail\":\"阳光维多利亚阳光。维多利亚3-1-13-3\",\"currentMeterFee\":\"0.00\"}]}";
//		
//		if(clientNumber.equals("10085"))
//		payInfo = "{\"customer\":[{\"adr\":\"阳光新院,维多利亚3-1-13-3\",\"yhname\":\"熊淑文\",\"yhcode\":\"03301040\",\"LandPhone\":\"15298015712\",\"abstractTotalMoney\":\"0.00\",\"bcye\":\"0.00\"}],\"historyAbstractDetail\":[],\"historyOne\":[],\"historyThree\":[],\"payInfo\":[{\"addressID\":\"cf648b9d-b700-4cc6-a761-ceff31449913\",\"meterSerialNo\":\"1\",\"meterType\":\"PB\",\"meterName\":\"基表\",\"addressDetail\":\"阳光维多利亚阳光。维多利亚3-1-13-3\",\"currentMeterFee\":\"0.00\"},{\"addressID\":\"cf648b9d-b700-4cc6-a761-ceff31449913\",\"meterSerialNo\":\"1\",\"meterType\":\"YC\",\"meterName\":\"纯远传表\",\"addressDetail\":\"阳光维多利亚阳光。维多利亚3-1-13-3\",\"currentMeterFee\":\"0.00\"}]}";
		
		System.out.println(payInfo);
		
		if("".equals(payInfo))
		{
			resp.setCode(-1);
			resp.setMessage("连接燃气公司超时，请重新点击登录！");
			return new JsonModelAndView(resp);
		}
        
        JSONObject json_company = JSONObject.fromObject(companyInfo);
        
        JSONObject json_pay = JSONObject.fromObject(payInfo);

        JSONArray customerInfoArray = (JSONArray)json_pay.get("customer");
        
        if(customerInfoArray.size()==0)
        {
        	resp.setCode(-1);
			resp.setMessage("无该燃气用户信息，请重新录入！！！");
			return new JsonModelAndView(resp);
        }

        json_pay.putAll(json_company);
        
		resp.setData(json_pay.toString());
		
		HttpSession session = request.getSession(false);
		
		if (session != null) {
			
			System.out.println("session增加userInfo:"+clientNumber+" 成功 sessionId:"+request.getRequestedSessionId());
			
			session.setAttribute("userInfo", clientNumber);// add session
		} else {
			System.out.println("authorize session失效，没有创建");
		}
		
		return new JsonModelAndView(resp);
		
	}
	
	@RequestMapping("/historyPayInfo.do")
	@ResponseBody
	public List<HistoryPayInfoForm> historyQuery(HttpServletRequest request,
			HttpServletResponse response) throws IOException
	{
        
		String customerID  = request.getParameter("customerID");
		
		String companyBM = request.getParameter("companyBM");
		
		
		HistoryPayInfoForm form = new HistoryPayInfoForm();

		List<HistoryPayInfoForm> list = new ArrayList<HistoryPayInfoForm>();
		
		list.add(form);

		return list;
	
	}
	
	
	@RequestMapping("/abstractInfoQuery.do")
	@ResponseBody
	public List<AbstractInfoForm> abstractQuery(HttpServletRequest request,
			HttpServletResponse response) throws IOException
	{
	
		RespInfo resp = new RespInfo(0, "建立成功", null);
        
		String customerID  = request.getParameter("customerID");
		
		String companyBM = request.getParameter("companyBM");
				
		AbstractInfoForm form = new AbstractInfoForm();

		List<AbstractInfoForm> list = new ArrayList<AbstractInfoForm>();
		
		list.add(form);
		
        form = new AbstractInfoForm();
		
		list.add(form);
		
		resp.setData(list);
		
		return list;

	}
	
	@RequestMapping(value = "/alipayapi", 
			method = {RequestMethod.GET, RequestMethod.POST})
	public void alipayapi(HttpServletRequest request,Model model, HttpServletResponse response) throws IOException {
		PrintWriter out = response.getWriter();
		String payment_type = "1";
		//必填，不能修改
		//服务器异步通知页面路径
		String notify_url = "";
		//需http://格式的完整路径，不能加?id=123这类自定义参数

		//页面跳转同步通知页面路径
		String return_url = "http://www.hl-epay.com"
				+ "/payment/ali_return_url.do";
		//需http://格式的完整路径，不能加?id=123这类自定义参数，不能写成http://localhost/

		//商户订单号
//		String out_trade_no = request.getParameter("WIDout_trade_no");
		String out_trade_no = NetworkUtil.getFromBASE64(request.getParameter("order_id"));
		//商户网站订单系统中唯一订单号，必填

		//订单名称
//		String subject = request.getParameter("WIDsubject");
		String subject = "燃气充值";
		//必填

		//付款金额
//		String total_fee = request.getParameter("WIDtotal_fee");
		Float total_fee = Float.parseFloat(request.getParameter("total_money").toString())/100 ;
//		if(total_fee.startsWith(".")){
//			total_fee = "0"+total_fee;
//		}
		//必填

		
		//商家信息
		CompanyInfo companyInfo = payservice.getComInfoByOrderID(out_trade_no);

		//订单描述

//		String body = request.getParameter("WIDbody");
		String body = "燃气费用支付";
		//默认支付方式
		String paymethod = "bankPay";
		//必填
		//默认网银
		String defaultbank = request.getParameter("WIDdefaultbank");
		//必填，银行简码请参考接口技术文档

		if(defaultbank.equals("ALIPAY")){
			paymethod = "directPay";
		}

		
		//商品展示地址
//		String show_url = request.getParameter("WIDshow_url");
		String show_url = "";
		//需以http://开头的完整路径，例如：http://www.商户网址.com/myorder.html

		//防钓鱼时间戳
		String anti_phishing_key = "";
		//若要使用请调用类文件submit中的query_timestamp函数

		//客户端的IP地址
		String exter_invoke_ip = getIp(request);
		//非局域网的外网IP地址，如：221.0.0.1
		
		
		//////////////////////////////////////////////////////////////////////////////////
		
		//把请求参数打包成数组
		Map<String, String> sParaTemp = new HashMap<String, String>();
		sParaTemp.put("service", "create_direct_pay_by_user");
        //sParaTemp.put("partner", AlipayConfig.partner);//db
		sParaTemp.put("partner", companyInfo.getAli_partner());
		//sParaTemp.put("seller_email", AlipayConfig.seller_email);//db
		sParaTemp.put("seller_email", companyInfo.getAli_seller_email());
		sParaTemp.put("_input_charset", AlipayConfig.input_charset);
		sParaTemp.put("payment_type", payment_type);
		sParaTemp.put("notify_url", notify_url);
		sParaTemp.put("return_url", return_url);
		sParaTemp.put("out_trade_no", out_trade_no);
		sParaTemp.put("subject", subject);
		sParaTemp.put("total_fee", total_fee.toString());
		sParaTemp.put("body", body);
		if(!defaultbank.equals("ALIPAY")){
			sParaTemp.put("paymethod", paymethod);
			sParaTemp.put("defaultbank", defaultbank);
		}
		sParaTemp.put("show_url", show_url);
		sParaTemp.put("anti_phishing_key", anti_phishing_key);
		sParaTemp.put("exter_invoke_ip", exter_invoke_ip);
		
		String ali_key = companyInfo.getAli_key();
		//建立请求
		String sHtmlText = AlipaySubmit.buildRequest(sParaTemp,"post","确认",ali_key);
		System.out.println("递交的请求xml:"+sHtmlText);
		out.println(sHtmlText);
	}
	
	@RequestMapping("/unionpay.do")
	public void userPayCashOfCard(HttpServletRequest request,
			HttpServletResponse response,Model model) throws Exception
	{	

		RespInfo resp = new RespInfo(0, "建立成功", null);

		String total_fee = String.valueOf((request.getParameter("total_money")));
		
		String order_id = NetworkUtil.getFromBASE64(request.getParameter("order_id"));

		logger.info("total_fee:"+total_fee+" order_id:"+order_id);
		
		CompanyInfo companyInfo = payservice.getComInfoByOrderID(order_id);

		if (companyInfo == null) {
			
			logger.error("公司信息取值异常！" + order_id);

			throw new Exception("取公司信息异常，联系技术人员");
		}
		
		String certPwd =  companyInfo.getBz2().trim();
		
		String certPath = "";
		
		if(request.getSession(false)!=null)
		{
		   certPath = request.getSession().getServletContext().getRealPath("/WEB-INF/certs/"+companyInfo.getBz1().trim());
		}
		else
		{
			System.out.println("union pay session 失效");
		}
		
		
		/**设置请求参数------------->**/
		Map<String, String> requestData = new HashMap<String, String>();
		String txnTime = request.getParameter("txnTime");
		
		/***银联全渠道系统，产品参数，除了encoding自行选择外其他不需修改***/
		requestData.put("version", UnionBase.version);   //版本号 
		requestData.put("encoding", UnionBase.encoding); //字符集编码 
		requestData.put("signMethod", "01");            //签名方法 
		requestData.put("txnType", "01");               //交易类型 01：消费，
		requestData.put("txnSubType", "01");            //交易子类型 01：自助消费
		requestData.put("bizType", "000201");           //业务类型 B2C网关支付
		requestData.put("channelType", "07");           //渠道类型 07：PC
		
		/***商户接入参数***/
		requestData.put("merId", companyInfo.getMerchantNum());    //商户号码
		
		requestData.put("accessType", "0");             //接入类型
		requestData.put("orderId",order_id);             //商户订单号		
		requestData.put("txnTime", txnTime);            //订单发送时间
		requestData.put("currencyCode", "156");         //交易币种
		requestData.put("txnAmt", total_fee);              //交易金额
		requestData.put("reqReserved", "透传字段");        //请求方保留域		
		
		requestData.put("frontUrl", UnionBase.frontUrl);
		
		requestData.put("backUrl", UnionBase.backUrl);

		Map<String, String> submitFromData = UnionBase.signData(requestData,certPath,certPwd);
		
		String requestFrontUrl = SDKConfig.getConfig().getFrontRequestUrl();
		
		resp.setData(org.json.JSONObject.valueToString(submitFromData));
		
		String html = UnionBase.createHtml(requestFrontUrl, submitFromData);

		System.out.println(html);
		
		response.getWriter().write(html);
	}
	
	
	/**
	 * 确认支付，并生成订单号
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping(value="/chargeOrder.do")
	public String chargeOrder(HttpServletRequest request,
			HttpServletResponse response,Model model) throws Exception
	{	

		
		String customerName = request.getParameter("");
		
		model.addAttribute("customerName",customerName);
		
		return "pay-casher-third";
		
	}
	
	
	/**
	 * 加载当前页面的table
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping("/userPayinfo.do")
	@ResponseBody
	public List<PayInfoForm> userPayInfoQuery(HttpServletRequest request,
			HttpServletResponse response)
	{	

		RespInfo resp = new RespInfo(0, "建立成功", null);
        
		String customerID  = request.getParameter("customerID");
		
		String companyBM = request.getParameter("companyBM");

		PayInfoForm form = new PayInfoForm();

		List<PayInfoForm> list = new ArrayList<PayInfoForm>();
		
		list.add(form);
		
        form = new PayInfoForm();
				
		list.add(form);

		resp.setData(list);

		return list;
	}

	@RequestMapping("/queryDetailPayment.do")
	public JsonModelAndView queryDetailPayment(HttpServletRequest request,
			HttpServletResponse response)
	{	

		String orderID =  NetworkUtil.getFromBASE64((String)request.getParameter("orderID"));
		
		return new JsonModelAndView(payservice.queryDetailByOrderID(orderID));
	}
	
	
	@RequestMapping("/orderInfo.do")
	public JsonModelAndView orderInfo(HttpServletRequest request,
			HttpServletResponse response)
	{
	
		RespInfo resp = new RespInfo(0, "建立成功", null);
		
		String orderid = NetworkUtil.getFromBASE64(request.getParameter("order_num"));
				
		int totalMoney = payservice.queryOrderMoney(orderid);
			
		resp.setData(String.valueOf(totalMoney));
		
		return new JsonModelAndView(resp);
	}
	
	@RequestMapping("/show_pay_state.do")
	public String showPaymentState(HttpServletRequest request,
			HttpServletResponse response,Model model)
	{
		boolean state = false;
		
		RespInfo resp = new RespInfo(0, "建立成功", null);
		
		String orderid = NetworkUtil.getFromBASE64(request.getParameter("order_num"));
		
		model.addAttribute("orderID",orderid);
		
		state = payservice.isPayStatusSuccessed(orderid);
		
		return state==true?"pay-success":"pay-failed";
	}
	
	@RequestMapping("/hrefresh_pay_state.do")
	public String hrefreshPaymentState(HttpServletRequest request,
			HttpServletResponse response,Model model) throws RemoteException, MalformedURLException, ServiceException, InterruptedException
	{
        boolean state = false;
		
		RespInfo resp = new RespInfo(0, "建立成功", null);
		
		
		String orderid = NetworkUtil.getFromBASE64(request.getParameter("order_num"));
		
		//String total_Money = request.getParameter("amount");

		state = payservice.isPayStatusSuccessed(orderid);
		
		String total_Money="";
		
		if (state == false)
			total_Money = String.valueOf(payservice.queryOrderMoney(orderid));

		else
			total_Money = String.valueOf(payservice.queryOrderMoney2(orderid));
		
		String txnTime = request.getParameter("txnTime");
		
		model.addAttribute("orderID",orderid);
		
//		model.addAttribute("fee",Double.valueOf(total_Money));
		
		model.addAttribute("fee",total_Money);
		
		model.addAttribute("feevisual",Double.valueOf(total_Money)/100);
		
		model.addAttribute("txnTime",txnTime);
		
		
		
		if(state==false)
		{
			Map<String, String> data = new HashMap<String, String>();
			
			/***银联全渠道系统，产品参数，除了encoding自行选择外其他不需修改***/
			data.put("version", UnionBase.version);                 //版本号
			data.put("encoding", UnionBase.encoding);               //字符集编码 
			data.put("signMethod", "01");                          //签名方法 目前只支持01-RSA方式证书加密
			data.put("txnType", "00");                             //交易类型 
			data.put("txnSubType", "00");                          //交易子类型  
			data.put("bizType", "000201");                         //业务类型 
			
			CompanyInfo companyInfo = payservice.getComInfoByOrderID(orderid);
			
			if(companyInfo==null)return "pay-fail-info";
			
			data.put("merId", companyInfo.getMerchantNum());                  //商户号码
			data.put("accessType", "0");                           //接入类型
			
			/***要调通交易以下字段必须修改***/
			data.put("orderId", orderid);                 //****商户订单号，每次发交易测试需修改为被查询的交易的订单号
			data.put("txnTime", txnTime);                 //****订单发送时间，每次发交易测试需修改为被查询的交易的订单发送时间

			/**请求参数设置完毕，以下对请求参数进行签名并发送http post请求，接收同步应答报文------------->**/
			
//			Map<String, String> submitFromData = UnionBase.signData(data);//报文中certId,signature的值是在signData方法中获取并自动赋值的，只要证书配置正确即可。
//
//			String url = SDKConfig.getConfig().getSingleQueryUrl();// 交易请求url从配置文件读取对应属性文件acp_sdk.properties中的 acpsdk.singleQueryUrl
//
//			Map<String, String> resmap = UnionBase.submitUrl(submitFromData, url);
	
			String certPath = "";
			
			String certPwd =  companyInfo.getBz2().trim();
			
			if(request.getSession(false)!=null)
			{
			   certPath = request.getSession().getServletContext().getRealPath("/WEB-INF/certs/"+companyInfo.getBz1().trim());
			}
			else
			{
				System.out.println("union pay session 失效");
			}
			
			Map<String, String> submitFromData = UnionBase.signData(data,certPath,certPwd);	
			
			String url = SDKConfig.getConfig().getSingleQueryUrl();// 交易请求url从配置文件读取对应属性文件acp_sdk.properties中的 acpsdk.singleQueryUrl

			SDKConfig.getConfig().setValidateCertDir(request.getSession().getServletContext().getRealPath("/WEB-INF/certs/"));
			
			//发送请求报文并接受同步应答（默认连接超时时间30秒，读取返回结果超时时间30秒）;这里调用signData之后，调用submitUrl之前不能对submitFromData中的键值对做任何修改，如果修改会导致验签不通过
			Map<String, String> resmap = UnionBase.submitUrl(submitFromData, url);

			logger.info("手动刷新,"+"订单号为:"+orderid+"返回状态码:"+resmap.get("respCode"));
			
			if(resmap.get("respCode")==null)return "pay-failed";
			
			if(resmap.get("respCode").equals("00")){//如果查询交易成功
				//处理被查询交易的应答码逻辑
				if(resmap.get("origRespCode").equals("00")){

					payservice.updatePayStatusSuccess(orderid);

					payservice.hl6UpdatePayStatus(orderid,companyInfo,"union");
					
					state = true;
	
				}else if(resmap.get("origRespCode").equals("03") ||
						 resmap.get("origRespCode").equals("04") ||
						 resmap.get("origRespCode").equals("05")){
				  
				}else{
					//其他应答码为失败请排查原因
					//TODO
				}
			}else{//查询交易本身失败，或者未查到原交易，检查查询交易报文要素
				//TODO
			}
			
			logger.info("回馈响应码:"+resmap.get("respCode"));
			
		}
		
		return state==true?"pay-success":"pay-failed";
	}
	
	@RequestMapping("/successNotice.do")
	public String successNotice(HttpServletRequest request,
			HttpServletResponse response,Model model)
	{
		
         String orderid = NetworkUtil.getFromBASE64(request.getParameter("order_num"));
		
	     String total_Money = request.getParameter("amount");
		
        //String total_Money = String.valueOf(payservice.queryOrderHistoryMoney(orderid));
       
		//String txnTime = request.getParameter("txnTime");
		
		model.addAttribute("orderID",orderid);
		
//		model.addAttribute("fee",Double.valueOf(total_Money));
		
		model.addAttribute("feevisual",Double.valueOf(total_Money)/100);

		System.out.println("成功支付刷新:"+orderid);
		
		return "pay-success";
	}
	
	
	
	@RequestMapping("/refresh_pay_state.do")
	public JsonModelAndView refreshPaymentState(HttpServletRequest request,
			HttpServletResponse response,Model model)
	{
		boolean state = false;
		
		RespInfo resp = new RespInfo(0, "建立成功", null);

		String orderid = NetworkUtil.getFromBASE64(request.getParameter("order_num"));
		
//		String total_Money = request.getParameter("amount");		
//		System.out.println("orderid:"+orderid+" total_Money:"+total_Money);
		model.addAttribute("orderID",orderid);
//		model.addAttribute("fee",Float.valueOf(total_Money)/100);
		
		state = payservice.isPayStatusSuccessed(orderid);
		
		resp.setCode(state==true?0:-1);
		
		return new JsonModelAndView(resp);
	}
	
	@RequestMapping(value = "/ali_notify_url", 
			method = {RequestMethod.GET, RequestMethod.POST})
	public void notify_url(HttpServletRequest request,Model model, HttpServletResponse response) throws IOException {
		PrintWriter out = response.getWriter();
		//获取支付宝GET过来反馈信息
		Map<String,String> params = new HashMap<String,String>();
		Map requestParams = request.getParameterMap();
		for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
			String name = (String) iter.next();
			String[] values = (String[]) requestParams.get(name);
			String valueStr = "";
			for (int i = 0; i < values.length; i++) {
				valueStr = (i == values.length - 1) ? valueStr + values[i]
						: valueStr + values[i] + ",";
			}
			//乱码解决，这段代码在出现乱码时使用。如果mysign和sign不相等也可以使用这段代码转化
			//valueStr = new String(valueStr.getBytes("ISO-8859-1"), "utf-8");
			params.put(name, valueStr);
		}
		
		//获取支付宝的通知返回参数，可参考技术文档中页面跳转同步通知参数列表(以下仅供参考)//
		//商户订单号

		String out_trade_no = request.getParameter("out_trade_no");

		//商家信息
		CompanyInfo companyInfo = payservice.getComInfoByOrderID(out_trade_no);

		//支付宝交易号

		String trade_no = request.getParameter("trade_no");

		//交易状态
		String trade_status = request.getParameter("trade_status");
		
		String total_fee  = request.getParameter("total_fee");

		//获取支付宝的通知返回参数，可参考技术文档中页面跳转同步通知参数列表(以上仅供参考)//
		
		//计算得出通知验证结果
		boolean verify_result = AlipayNotify.verify(params,companyInfo.getAli_partner().trim(),companyInfo.getAli_key().trim());
		
		if(verify_result){//验证成功
			//////////////////////////////////////////////////////////////////////////////////////////
			//请在这里加上商户的业务逻辑程序代码

			//——请根据您的业务逻辑来编写程序（以下代码仅作参考）——
/*			if(trade_status.equals("TRADE_FINISHED") || trade_status.equals("TRADE_SUCCESS")){
				//判断该笔订单是否在商户网站中已经做过处理
				//如果没有做过处理，根据订单号（out_trade_no）在商户网站的订单系统中查到该笔订单的详细，并执行商户的业务程序
				//如果有做过处理，不执行商户的业务程序
				return;
			}*/
				out.println("success");	
		}else{
			//该页面可做页面美工编辑
			System.out.println("银联接口异步处理失败！"+trade_no);
			out.println("fail");
		}
	}
	
	
	@RequestMapping(value = "/ali_return_url.do", 
			method = {RequestMethod.GET, RequestMethod.POST})
	public String return_url(HttpServletRequest request, HttpServletResponse response,Model model) throws IOException, ServiceException 
	{
		PrintWriter out = response.getWriter();
		//获取支付宝GET过来反馈信息
		Map<String,String> params = new HashMap<String,String>();
		Map requestParams = request.getParameterMap();
		for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
			String name = (String) iter.next();
			String[] values = (String[]) requestParams.get(name);
			String valueStr = "";
			for (int i = 0; i < values.length; i++) {
				valueStr = (i == values.length - 1) ? valueStr + values[i]
						: valueStr + values[i] + ",";
			}
			//乱码解决，这段代码在出现乱码时使用。如果mysign和sign不相等也可以使用这段代码转化
			valueStr = new String(valueStr.getBytes("ISO-8859-1"), "utf-8");
			params.put(name, valueStr);
		}
		
		System.out.println("支付宝返回参数:"+params.toString());
		
		String out_trade_no = request.getParameter("out_trade_no");

		boolean isHavedNotPaid = payservice.isSureNotPayOrder(out_trade_no);
		
		if(!isHavedNotPaid)
		{	
			System.out.println("流水号:"+out_trade_no+"重复通知，已经支付处理完成");
			
			return "";
		}
			
		//商家信息
		CompanyInfo companyInfo = payservice.getComInfoByOrderID(out_trade_no);
		
		//支付宝交易号
//		String trade_no = request.getParameter("trade_no");

		//交易状态
		String trade_status = request.getParameter("trade_status");
	
		String total_fee  = request.getParameter("total_fee");	

		boolean verify_result = AlipayNotify.verify(params,companyInfo.getAli_partner(),companyInfo.getAli_key());
		
		model.addAttribute("orderID", out_trade_no);

		model.addAttribute("feevisual", total_fee);
		
		if (verify_result) {// 验证成功

			if (trade_status.equals("TRADE_FINISHED")
					|| trade_status.equals("TRADE_SUCCESS")) {
				
				payservice.updatePayStatusSuccess(out_trade_no);
				
				payservice.hl6UpdatePayStatus(out_trade_no, companyInfo,
						"ali_portal");

				
				System.out.println("阿里支付成功支付:" + out_trade_no);

				return "pay-success";
			} else {
				model.addAttribute("reason","阿里支付失败,请联系后台管理人员!");
				
				System.out.println("流水号:"+out_trade_no+"阿里支付失败,请联系后台管理人员");
				
				return "pay-failed";
			}
		}
		else
		{
			model.addAttribute("reason","校验码错误,支付宝付款成功，但没有进入海力数据中心，请联系后台管理人员!");
			
			System.out.println("流水号:"+out_trade_no+"校验码错误,支付宝付款成功，但没有进入海力数据中心，请联系后台管理人员!");
			
			return "pay-failed";
		}
	}
	
	
	@RequestMapping("/wx_refundPay.do")
	public void refundPayOrder_WX(HttpServletRequest request,
			HttpServletResponse response) throws Exception {

		String out_trade_id = request.getParameter("orderId");
		
		Map<String,Object> union_map = payservice.getRefundUnionByOrderID(out_trade_id);
	
		CompanyInfo companyInfo = (CompanyInfo)union_map.get("comapnyInfo");
		
		ProductionOrdering2 productionOrdering = (ProductionOrdering2)union_map.get("orderInfo");
		
		if(companyInfo==null&&productionOrdering==null){throw new Exception(out_trade_id+":退款订单数据异常");}
		
        HashMap<String, String> paramMap = Maps.newHashMap(); 
 

		paramMap.put("out_trade_no", out_trade_id); // 订单号
		paramMap.put("out_refund_no", "RE"+out_trade_id); // 退单号
		paramMap.put("total_fee", String.valueOf((int)(productionOrdering.getTotalMoney()*100))); // 总金额
		paramMap.put("refund_fee",String.valueOf((int)(productionOrdering.getTotalMoney()*100))); // 总金额
		paramMap.put("op_user_id",companyInfo.getWx_mch_num());
		paramMap.put("appid", companyInfo.getWx_appid()); // appid
		paramMap.put("mch_id", companyInfo.getWx_mch_num()); // 商户号
		paramMap.put("nonce_str", Util.CreateNoncestr()); // 随机数
		paramMap.put("sign", Signature.getSign(paramMap));// 根据微信签名规则，生成签名
		
		String postDataXML = MmToolkit.ArrayToXml(paramMap);
		
		logger.info(postDataXML);
		
		String path = request.getSession().getServletContext().getRealPath("/WEB-INF/certs/"+companyInfo.getWx_cert().trim());
	
		companyInfo.setWx_cert(path);
		
        byte[] resXmlBuf = RpcServletTool.postHttpsData("https://api.mch.weixin.qq.com/secapi/pay/refund", postDataXML,companyInfo);
        
        String content = new String(resXmlBuf,"utf-8");//将byte数组 数据转换为字符串
		
        logger.info(content);
	
	}
	
	/**
	 * WeiXin Back
	 * @param request
	 * @param response
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	@RequestMapping("/wxatm_pay_notify.do")
	public void doPayOrderStatus_ATM_WX(HttpServletRequest request,
			HttpServletResponse response) throws Exception
	{
		
			
		//modified by ......
		//读取参数
				InputStream inputStream ;
				StringBuffer sb = new StringBuffer();
				inputStream = request.getInputStream();
				String s ;
				BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
				while ((s = in.readLine()) != null){
					sb.append(s);
				}
				in.close();
				inputStream.close();

				//解析xml成map
				Map<String, String> m = new HashMap<String, String>();
//				String ss= "<xml>" +
//						"<appid><![CDATA[wx869e56492042a94f]]></appid>" +
//						"<attach><![CDATA[支付测试]]></attach>" +
//						"<bank_type><![CDATA[CFT]]></bank_type>" +
//						"<fee_type><![CDATA[CNY]]></fee_type>" +
//						"<is_subscribe><![CDATA[Y]]></is_subscribe>" +
//						"<mch_id><![CDATA[10000100]]></mch_id>" +
//						"<nonce_str><![CDATA[5d2b6c2a8db53831f7eda20af46e531c]]></nonce_str>" +
//						"<openid><![CDATA[oUpF8uMEb4qRXf22hE3X68TekukE]]></openid>" +
//						"<out_trade_no><![CDATA[20160819165816abcdefg]]></out_trade_no>" +
//						"<result_code><![CDATA[SUCCESS]]></result_code>" +
//						"<return_code><![CDATA[SUCCESS]]></return_code>" +
//						"<sign><![CDATA[B552ED6B279343CB493C5DD0D78AB241]]></sign>" +
//						"<sub_mch_id><![CDATA[10000100]]></sub_mch_id>" +
//						"<time_end><![CDATA[20140903131540]]></time_end>" +
//						"<total_fee>1</total_fee>" +
//						"<trade_type><![CDATA[JSAPI]]></trade_type>" +
//						"<transaction_id><![CDATA[1004400740201409030005092168]]></transaction_id>" +
//						"</xml>";
				
				m = MmToolkit.doXMLParse(sb.toString());
//				m = MmToolkit.doXMLParse(ss);
				//过滤空 设置 TreeMap
				SortedMap<Object,Object> packageParams = new TreeMap<Object,Object>();		
				Iterator it = m.keySet().iterator();
				while (it.hasNext()) {
					String parameter = (String) it.next();
					String parameterValue = m.get(parameter);
					
					String v = "";
					if(null != parameterValue) {
						v = parameterValue.trim();
					}
					packageParams.put(parameter, v);
				}
				
				// 账号信息
//		        String key = PayConfigUtil.API_KEY; // key
				CompanyInfo companyInfo = payservice.getComInfoByOrderID((String)packageParams.get("out_trade_no"));
		       if (companyInfo == null) {
//			       throw new Exception("订单号已经处理正常或根据订单号无公司信息，查询错误，需要联系管理员");
		           logger.info((String)packageParams.get("out_trade_no")+"订单号已经得到处理!");
		    	   return ;
		       }
			   
		        String key = companyInfo.getWx_key();
				
		        logger.info(packageParams);
//			       判断签名是否正确
			    if(MmToolkit.isTenpaySign("UTF-8", packageParams,key)) {
//			    if(true){ 
		        //------------------------------
			        //处理业务开始
			        //------------------------------
			        String resXml = "";
			        if("SUCCESS".equals((String)packageParams.get("result_code"))){
			        	// 这里是支付成功
			            //////////执行自己的业务逻辑////////////////
			        	String mch_id = (String)packageParams.get("mch_id");
			        	String openid = (String)packageParams.get("openid");
			        	String is_subscribe = (String)packageParams.get("is_subscribe");
			        	String out_trade_no = (String)packageParams.get("out_trade_no");
			        	
			        	String total_fee = (String)packageParams.get("total_fee");
			        	
			        	logger.info("mch_id:"+mch_id);
			        	logger.info("openid:"+openid);
			        	logger.info("is_subscribe:"+is_subscribe);
			        	logger.info("out_trade_no:"+out_trade_no);
			        	logger.info("total_fee:"+total_fee);
			            
			        	payservice.updatePayStatusSuccess(out_trade_no);

//						CompanyInfo companyInfo = payservice.getComInfoByOrderID(order_id);

						payservice.hl6AtmUpdatePayStatus(out_trade_no, companyInfo,"wx_atm_portal");
						
						logger.info("微信订单号回调完成:"+out_trade_no+",支付成功");
			        	
			            //通知微信.异步确认成功.必写.不然会一直通知后台.八次之后就认为交易失败了.
			            resXml = "<xml>" + "<return_code><![CDATA[SUCCESS]]></return_code>"
			                    + "<return_msg><![CDATA[OK]]></return_msg>" + "</xml> ";
			            
			        } else {
			        	logger.info("微信支付失败,错误信息：" + packageParams.get("err_code"));
			            resXml = "<xml>" + "<return_code><![CDATA[FAIL]]></return_code>"
			                    + "<return_msg><![CDATA[报文为空]]></return_msg>" + "</xml> ";
			        }
			        //------------------------------
			        //处理业务完毕
			        //------------------------------
			        BufferedOutputStream out = new BufferedOutputStream(
			                response.getOutputStream());
			        out.write(resXml.getBytes());
			        out.flush();
			        out.close();
			    } else{
			    	logger.info("通知签名验证失败");
			    }
		//end this paragraph
		
	}
	
	
	/**
	 * WeiXin Back
	 * @param request
	 * @param response
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	@RequestMapping("/wx_pay_notify.do")
	public void doPayOrderStatus_WX(HttpServletRequest request,
			HttpServletResponse response) throws Exception
	{
		
			
		//modified by ......
		//读取参数
				InputStream inputStream ;
				StringBuffer sb = new StringBuffer();
				inputStream = request.getInputStream();
				String s ;
				BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
				while ((s = in.readLine()) != null){
					sb.append(s);
				}
				in.close();
				inputStream.close();

				//解析xml成map
				Map<String, String> m = new HashMap<String, String>();
//				String ss= "<xml>" +
//						"<appid><![CDATA[wx869e56492042a94f]]></appid>" +
//						"<attach><![CDATA[支付测试]]></attach>" +
//						"<bank_type><![CDATA[CFT]]></bank_type>" +
//						"<fee_type><![CDATA[CNY]]></fee_type>" +
//						"<is_subscribe><![CDATA[Y]]></is_subscribe>" +
//						"<mch_id><![CDATA[10000100]]></mch_id>" +
//						"<nonce_str><![CDATA[5d2b6c2a8db53831f7eda20af46e531c]]></nonce_str>" +
//						"<openid><![CDATA[oUpF8uMEb4qRXf22hE3X68TekukE]]></openid>" +
//						"<out_trade_no><![CDATA[20160819165816abcdefg]]></out_trade_no>" +
//						"<result_code><![CDATA[SUCCESS]]></result_code>" +
//						"<return_code><![CDATA[SUCCESS]]></return_code>" +
//						"<sign><![CDATA[B552ED6B279343CB493C5DD0D78AB241]]></sign>" +
//						"<sub_mch_id><![CDATA[10000100]]></sub_mch_id>" +
//						"<time_end><![CDATA[20140903131540]]></time_end>" +
//						"<total_fee>1</total_fee>" +
//						"<trade_type><![CDATA[JSAPI]]></trade_type>" +
//						"<transaction_id><![CDATA[1004400740201409030005092168]]></transaction_id>" +
//						"</xml>";
				
				m = MmToolkit.doXMLParse(sb.toString());
//				m = MmToolkit.doXMLParse(ss);
				//过滤空 设置 TreeMap
				SortedMap<Object,Object> packageParams = new TreeMap<Object,Object>();		
				Iterator it = m.keySet().iterator();
				while (it.hasNext()) {
					String parameter = (String) it.next();
					String parameterValue = m.get(parameter);
					
					String v = "";
					if(null != parameterValue) {
						v = parameterValue.trim();
					}
					packageParams.put(parameter, v);
				}
				
				// 账号信息
//		        String key = PayConfigUtil.API_KEY; // key
				CompanyInfo companyInfo = payservice.getComInfoByOrderID((String)packageParams.get("out_trade_no"));
		       if (companyInfo == null) {
//			       throw new Exception("订单号已经处理正常或根据订单号无公司信息，查询错误，需要联系管理员");
		           logger.info((String)packageParams.get("out_trade_no")+"订单号已经得到处理!");
		    	   return ;
		       }
			   
		        String key = companyInfo.getWx_key();
				
		        logger.info(packageParams);
//			       判断签名是否正确
			    if(MmToolkit.isTenpaySign("UTF-8", packageParams,key)) {
//			    if(true){ 
		        //------------------------------
			        //处理业务开始
			        //------------------------------
			        String resXml = "";
			        if("SUCCESS".equals((String)packageParams.get("result_code"))){
			        	// 这里是支付成功
			            //////////执行自己的业务逻辑////////////////
			        	String mch_id = (String)packageParams.get("mch_id");
			        	String openid = (String)packageParams.get("openid");
			        	String is_subscribe = (String)packageParams.get("is_subscribe");
			        	String out_trade_no = (String)packageParams.get("out_trade_no");
			        	
			        	String total_fee = (String)packageParams.get("total_fee");
			        	
			        	logger.info("mch_id:"+mch_id);
			        	logger.info("openid:"+openid);
			        	logger.info("is_subscribe:"+is_subscribe);
			        	logger.info("out_trade_no:"+out_trade_no);
			        	logger.info("total_fee:"+total_fee);
			            
			        	payservice.updatePayStatusSuccess(out_trade_no);

//						CompanyInfo companyInfo = payservice.getComInfoByOrderID(order_id);

						payservice.hl6UpdatePayStatus(out_trade_no, companyInfo,"wx_portal");
						
						logger.info("微信订单号回调完成:"+out_trade_no+",支付成功");
			        	
			            //通知微信.异步确认成功.必写.不然会一直通知后台.八次之后就认为交易失败了.
			            resXml = "<xml>" + "<return_code><![CDATA[SUCCESS]]></return_code>"
			                    + "<return_msg><![CDATA[OK]]></return_msg>" + "</xml> ";
			            
			        } else {
			        	logger.info("微信支付失败,错误信息：" + packageParams.get("err_code"));
			            resXml = "<xml>" + "<return_code><![CDATA[FAIL]]></return_code>"
			                    + "<return_msg><![CDATA[报文为空]]></return_msg>" + "</xml> ";
			        }
			        //------------------------------
			        //处理业务完毕
			        //------------------------------
			        BufferedOutputStream out = new BufferedOutputStream(
			                response.getOutputStream());
			        out.write(resXml.getBytes());
			        out.flush();
			        out.close();
			    } else{
			    	logger.info("通知签名验证失败");
			    }
		//end this paragraph
		
	}
	
	/**
	 * open-WXIN-HL6
	 * @param request
	 * @param response
	 * @throws Exception 
	 */
	@SuppressWarnings({ "unchecked", "finally" })
	@RequestMapping("/noticeHlService.do")
	public ModelAndView doNoticeHL_WX(HttpServletRequest request,
			HttpServletResponse response) throws Exception
	{
		
		        
		        int isSuccessed = -1;        
		
		        String customerNumber = request.getParameter("CUSTOMERID");
		        String orderId = request.getParameter("ORDERID");
		        String totalMoney = request.getParameter("TOTALFEE");
		        String meterSerialNo = request.getParameter("meterSerialNo");
		        String addressID = request.getParameter("addressID");
		        String companyCode = request.getParameter("companyCode");
		        
		        CompanyInfo companyInfo = payservice.getComInfoByCode(companyCode);
		        
		        if(companyInfo==null)return new JsonModelAndView(new RespInfo(-1, "公司编码不存在", null));
		        
		        logger.info("微信公众支付使用HL6:"+customerNumber+"|"+orderId+"|"+totalMoney+"|"+meterSerialNo+"|"+addressID);
		        	   
			   	Map<String, String> params = new HashMap<String, String>();
			    params.put("CUSTOMERID",customerNumber);//1
			    params.put("ORDERID",orderId);//1
			    params.put("TOTALFEE",totalMoney.toString());//1
			    
			    
			    Date nowTime = new Date(System.currentTimeMillis());
				  SimpleDateFormat sdFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				  String retStrFormatNowDate = sdFormatter.format(nowTime);
			    
			    params.put("CREATEDATE",retStrFormatNowDate);
			    params.put("meterSerialNo",meterSerialNo);//2
			    params.put("FEE",totalMoney.toString());//2
			    params.put("SERIALNUM",orderId);//2
			    params.put("addressID",addressID);//2

		try {
			isSuccessed = (short) RpcServletTool
					.callAsmxGenerateFileWebService(
							RpcServletTool.combineQrCustomerInfoURL(
									companyInfo.getCloudIp(),
									companyInfo.getPort()),
							"http://tempuri.org/", "GenerateChargeInfo",
							params, XMLType.XSD_SHORT);

		} catch (Exception e) {
			logger.error("微信公众订单号:" + orderId + ":" + e.getMessage() + "|"
					+ e.toString());
            
			isSuccessed = (short) RpcServletTool
					.callAsmxGenerateFileWebService(
							RpcServletTool.combineQrCustomerInfoURL(
									companyInfo.getCloudIp(),
									companyInfo.getPort()),
							"http://tempuri.org/", "GenerateChargeInfo",
							params, XMLType.XSD_SHORT);//twice reconnect
		}
		finally
		{
			return new JsonModelAndView(new RespInfo(isSuccessed, isSuccessed==0?"支付成功 ":"支付失败", null));
		}
	}
	
	
	
	
	/**
	 * 获取请求参数中所有的信息
	 * 
	 * @param request
	 * @return
	 */
	public static Map<String, String> getAllRequestParam(final HttpServletRequest request) {
		Map<String, String> res = new HashMap<String, String>();
		Enumeration<?> temp = request.getParameterNames();
		if (null != temp) {
			while (temp.hasMoreElements()) {
				String en = (String) temp.nextElement();
				String value = request.getParameter(en);
				res.put(en, value);
				//在报文上送时，如果字段的值为空，则不上送<下面的处理为在获取所有参数数据时，判断若值为空，则删除这个字段>
				//System.out.println("ServletUtil类247行  temp数据的键=="+en+"     值==="+value);
				if (null == res.get(en) || "".equals(res.get(en))) {
					res.remove(en);
				}
			}
		}
		return res;
	}
	
	
	
	@RequestMapping("/doPayOrderStatus.do")
	public void doPayOrderStatus(HttpServletRequest request,
			HttpServletResponse response) throws RemoteException, MalformedURLException, ServiceException
	{
		
		boolean state = false;
		
		RespInfo resp = new RespInfo(0, "建立成功", null);

		Map<String, String> reqParam = getAllRequestParam(request);
		
		com.hl.web.sdk.LogUtil.printRequestLog(reqParam);
		
//		String order_id = NetworkUtil.getFromBASE64(reqParam.get("orderId"));
		
		String order_id = reqParam.get("orderId");
		
		if (reqParam.get("respCode").equals("00")) {

			payservice.updatePayStatusSuccess(order_id);

			CompanyInfo companyInfo = payservice.getComInfoByOrderID(order_id);

			payservice.hl6UpdatePayStatus(order_id, companyInfo,"union");
			
			logger.info("订单号回调完成:"+order_id);
		}
		else
		{
			logger.info("订单号：" + order_id + "银联支付回调失败，与银联联系");
		}
	}
	
	private String getIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if(StringUtils.isNotEmpty(ip) && !"unKnown".equalsIgnoreCase(ip)){
            //多次反向代理后会有多个ip值，第一个ip才是真实ip
            int index = ip.indexOf(",");
            if(index != -1){
                return ip.substring(0,index);
            }else{
                return ip;
            }
        }
        ip = request.getHeader("X-Real-IP");
        if(StringUtils.isNotEmpty(ip) && !"unKnown".equalsIgnoreCase(ip)){
            return ip;
        }
        return request.getRemoteAddr();
    }
	
	
}
