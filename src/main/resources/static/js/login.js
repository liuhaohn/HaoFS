/**
 * login.html
 */

$(function() {
	// 回车键快捷操作
	$("body").keypress(function(e) {
		var keyCode = e.keyCode ? e.keyCode : e.which ? e.which : e.charCode;
		if (keyCode == 13) {
			var g = $("#loginBtn").click();
			return false;
		}
	});
	$("#vercodebox").html("");
	$("#vercodebox").removeClass("show");
	$("#vercodebox").addClass("hidden");
	// 打开页面自动聚焦账户输入框
	// $("#accountid").focus();
	// 询问是否可以显示注册按钮
	// $.ajax({
	// 	type : "POST",
	// 	dataType : "text",
	// 	data : {},
	// 	url : "homeController/askForAllowSignUpOrNot.ajax",
	// 	success : function(result) {
	// 		if (result == "true") {
	// 			$("#signupBox").removeClass("hidden");
	// 			$("#signupBox").addClass("show");
	// 			return;
	// 		}
	// 	},
	// 	error : function() {
	// 		alert("错误：无法连接到kiftd服务器，请检查您的网络连接或查看服务器运行状态。");
	// 	}
	// });
})

var loginFile;
var loginField;
// 点击文本框触发input:file选择文件动作
function checkLoginFile() {
	$('#uploadloginfile').click();
}

// 获取选中文件
function getLoginFile() {
	loginFile = $("#uploadloginfile").get(0).files[0];
	$("#loginpath").val(loginFile.name);
	readLoginFile();
}

function readLoginFile() {
	fr = new FileReader();
	fr.onload = function (){
		loginField = JSON.parse(this.result);
	}
	fr.readAsText(loginFile, "UTF-8");
}

function dologin_() {
	startLogin();
	// 加密认证-获取公钥并将请求加密发送给服务器，避免中途被窃取
	$.ajax({
		url : 'homeController/getPublicKey.ajax',
		type : 'POST',
		data : {},
		dataType : 'text',
		success : function(result) {
			var publicKeyInfo = eval("(" + result + ")");
			var date = new Date();// 这个是客户浏览器上的当前时间
			loginField.time = publicKeyInfo.time;
			// var loginInfo = '{accountId:"' + accountId + '",accountPwd:"'
			// 		+ accountPwd + '",time:"' + publicKeyInfo.time + '"}';
			var encrypt = new JSEncrypt();// 加密插件对象
			encrypt.setPublicKey(publicKeyInfo.publicKey);// 设置公钥
			var encrypted = encrypt.encryptLong(JSON.stringify(loginField));// 进行加密
			sendLoginInfo(encrypted);
		},
		error : function() {
			showAlert("提示：登录请求失败，请检查网络或服务器运行状态");
		}
	});
}

function sendLoginInfo(encrypted) {
	$.ajax({
		type : "POST",
		dataType : "text",
		url : "homeController/doLogin.ajax",
		data : {
			encrypted : encrypted,
			vercode : $("#vercode").val()
		},
		success : function(result) {
			$("#alertbox").removeClass("alert");
			$("#alertbox").removeClass("alert-danger");
			$("#alertbox").text("");
			$("#vercodebox").html("");
			$("#vercodebox").removeClass("show");
			$("#vercodebox").addClass("hidden");
			switch (result) {
				case "permitlogin":
					finishLogin();
					$("#accountidbox").removeClass("has-error");
					$("#accountpwdbox").removeClass("has-error");
					window.location.href = "/home.html";
					break;
				case "error":
					showAlert("提示：登录失败，登录请求无法通过效验（可能是请求耗时过长导致的）");
					break;
				default:
					showAlert("提示：无法登录，未知错误");
					break;
			}
		},
		error : function() {
			showAlert("提示：登录请求失败，请检查网络或服务器运行状态");
		}
	});
}

//获取一个新的验证码
function getNewVerCode(){
	$("#showvercode").attr("src","homeController/getNewVerCode.do?s="+(new Date()).getTime());
}

function showAlert(text){
	finishLogin();
	$("#alertbox").addClass("alert");
	$("#alertbox").addClass("alert-danger");
	$("#alertbox").text(text);
}

function startLogin(){
	$("#loginBtn").attr('disabled','disabled');
	$("#accountid").attr('disabled','disabled');
	$("#accountpwd").attr('disabled','disabled');
	$("#vercode").attr('disabled','disabled');
	$("#loginBtn").val('正在登录...');
}

function finishLogin(){
	$("#loginBtn").removeAttr('disabled');
	$("#accountid").removeAttr('disabled');
	$("#accountpwd").removeAttr('disabled');
	$("#vercode").removeAttr('disabled');
	$("#loginBtn").val('登录');
}