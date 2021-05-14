/*----------文件检索模块start----------*/
var file_search;  // 文件列表对象
function showSearchFileModel() {
    if (isUpLoading == false) {
        $("#filepath-search").removeAttr("disabled");
        $("#uploadfile-search").val("");
        $("#filepath-search").val("");
        $("#pros-search").width("0%");
        $("#pros-search").attr('aria-valuenow', '0');
        $("#umbutton-search").attr('disabled', false);
        $("#filecount-search").text("");
        $("#uploadstatus-search").html("");
        $("#selectcount-search").text("");
        $("#selectFileUpLoadModelAsAll-search").removeAttr("checked");
        $("#selectFileUpLoadModelAlert-search").hide();
    }
    $('#searchFileModal').modal('show');
}

// 点击文本框触发input:file选择文件动作
function checkpath_search() {
    $('#uploadfile-search').click();
}

// 获取选中文件
function getInputUpload_search() {
    file_search = $("#uploadfile-search").get(0).files[0];
    $("#filepath-search").val(file_search.name);
}


// 检查文件是否能够上传
function checkUploadFile_search() {
    if (isNaN(parseInt($("#hits-search")[0].value))) {
        showUploadFileAlert_search("提示：请检查返回数量是否正确");
        return
    }
    $("#uploadFileAlert-search").hide();
    $("#uploadFileAlert-search").text("");
    $("#uploadFileSuccess-search").hide();
    $("#uploadFileSuccess-search").text("");
    if (isUpLoading === false && isImporting === false) {
        if (file_search != null) {
            $("#filepath-search").attr("disabled", "disabled");
            $("#umbutton-search").attr('disabled', true);
            isUpLoading = true;
            repeModelList = null;
            $("#uploadFileAlert-search").hide();
            $("#uploadFileAlert-search").text("");
            var filenames = [file_search.name];
            var maxSize = file_search.size;
            var maxFileIndex = 0;
            var namelist = JSON.stringify(filenames);

            $.ajax({
                type: "POST",
                dataType: "text",
                data: {
                    namelist: namelist,
                    maxSize: maxSize,
                    maxFileIndex: maxFileIndex
                },
                url: "homeController/checkUploadCheckFile.ajax",
                success: function (result) {
                    if (result == "mustLogin") {
                        window.location.href = "prv/login.html";
                    } else {
                        switch (result) {
                            case "errorParameter":
                                showUploadFileAlert_search("提示：参数不正确，无法开始校验");
                                break;
                            default:
                                var resp = eval("(" + result + ")");
                                if (resp.checkResult == "fileTooLarge") {
                                    showUploadFileAlert_search("提示：文件["
                                        + resp.overSizeFile
                                        + "]的体积超过最大限制（"
                                        + resp.maxUploadFileSize
                                        + "），无法开始校验");
                                } else if (resp.checkResult == "permitUpload") {
                                    doupload_search();
                                } else {
                                    showUploadFileAlert_search("提示：出现意外错误，无法开始校验");
                                }
                                break;
                        }
                    }
                },
                error: function () {
                    showUploadFileAlert_search("提示：出现意外错误，无法开始校验");
                }
            });
        } else {
            showUploadFileAlert_search("提示：您未选择任何文件，无法开始校验");
        }
    } else {
        showUploadFileAlert_search("提示：另一项任务尚未完成，无法开始校验");
    }
}

// 执行文件上传并实现上传进度显示
function doupload_search() {
    var fcount = file_search.length;
    $("#pros-search").width("0%");// 先将进度条置0
    $("#pros-search").attr('aria-valuenow', "0");
    var uploadfile = file_search;// 获取要上传的文件
    if (uploadfile != null) {
        var fname = uploadfile.name;
        if (fcount > 1) {
            $("#filecount").text("（" + count + "/" + fcount + "）");// 显示当前进度
        }
        $("#uploadstatus-search").prepend(
            "<p>" + html2Escape(fname) + "<span id='uls_" + count
            + "'>[正在校验...]</span></p>");
        xhr = new XMLHttpRequest();// 这东西类似于servlet里面的request

        var fd = new FormData();// 用于封装文件数据的对象
        fd.append("image", uploadfile);// 将文件对象添加到FormData对象中，字段名为uploadfile
        fd.append("fname", fname);
        fd.append("hits", parseInt($("#hits-search")[0].value));

        xhr.open("POST", "homeController/getSearchView.ajax", true);// 上传目标

        xhr.upload.addEventListener("progress", uploadProgress_search, false);// 这个是对上传进度的监听
        // 上面的三个参数分别是：事件名（指定名称）、回调函数、是否冒泡（一般是false即可）

        xhr.send(fd);// 上传FormData对象

        if (pingInt == null) {
            pingInt = setInterval("ping()", 60000);// 上传中开始计时应答
        }

        // 上传结束后执行的回调函数
        xhr.onloadend = function () {
            // 停止应答计时
            if (pingInt != null) {
                window.clearInterval(pingInt);
                pingInt = null;
            }
            if (xhr.status === 200) {
                var result = xhr.responseText;
                if (result == null || result === "uploaderror") {
                    showUploadFileAlert_search("服务器出错，上传被中断。");
                } else if (result === "permissiondenied") {
                    showUploadFileAlert_search("权限受限。");
                } else {
                    showUploadFileSuccess_search(result);
                }
            } else {
                showUploadFileAlert_search("出现意外错误，上传被中断。");
            }
        };

    } else {
        showUploadFileAlert_search("提示：要上传的文件不存在。");
    }
}

// 显示上传文件进度
function uploadProgress_search(evt) {
    if (evt.lengthComputable) {
        // evt.loaded：文件上传的大小 evt.total：文件总的大小
        var percentComplete = Math.round((evt.loaded) * 100 / evt.total);
        // 加载进度条，同时显示信息
        $("#pros-search").width(percentComplete + "%");
        $("#pros-search").attr('aria-valuenow', "" + percentComplete);
    }
}

// 显示上传文件错误提示
function showUploadFileAlert_search(txt) {
    isUpLoading = false;
    $("#filepath-search").removeAttr("disabled");
    $("#uploadFileAlert-search").show();
    $("#uploadFileAlert-search").text(txt);
    $("#umbutton-search").attr('disabled', false);
}

// 显示文件校验成功提示
function showUploadFileSuccess_search(result) {
    if (result === "") return;
    startLoading();
    isUpLoading = false;
    $("#filepath-search").removeAttr("disabled");
    $("#uploadFileSuccess-search").show();
    $("#umbutton-search").attr('disabled', false);


    // 上述情况都不是，则返回的应该是文件夹视图数据，接下来对其进行解析
    folderView = eval("(" + result + ")");
    // 记录当前获取的文件夹视图的ID号，便于其他操作使用
    locationpath = folderView.folder.folderId;
    // 记录上级目录ID，方便返回上一级
    parentpath = folderView.folder.folderParent;
    // 记录本文件夹的访问级别，便于在新建文件夹时判断应该从哪一个级别开始供用户选择
    constraintLevel = folderView.folder.folderConstraint;
    screenedFoldrView = null;
    // 备份一份原始的文件夹视图数据，同时也记录下原始的查询偏移量
    originFolderView = $.extend(true, {}, folderView);
    totalFoldersOffset = folderView.foldersOffset;
    totalFilesOffset = folderView.filesOffset;
    // 搜索输入框重置
    $("#sreachKeyWordIn").val("");
    // 各项基于文件夹视图返回数据的解析操作……
    showParentList(folderView);
    showAccountView(folderView);
    showPublishTime(folderView);
    $("#sortByFN").removeClass();
    $("#sortByCD").removeClass();
    $("#sortByFS").removeClass();
    $("#sortByCN").removeClass();
    $("#sortByOR").removeClass();


    showSearchTable(folderView);
    // 更新文件夹信息至信息模态框
    $("#fim_name").text(folderView.folder.folderName);
    $("#fim_creator").text(folderView.folder.folderCreator);
    $("#fim_folderCreationDate").text(
        folderView.folder.folderCreationDate);
    $("#fim_folderId").text(folderView.folder.folderId);
    updateTheFolderInfo();
    // 判断是否还需要加载后续数据

    abortUpload_search();
    endLoading();
}

// 显示文件夹内容
function showSearchTable(folderView) {
    $("#foldertable").html("");
    for (var i2 = folderView.fileList.length; i2 > 0; i2--) {
        var fi = folderView.fileList[i2 - 1];
        var a = fi.fileCreator === account;
        var b = fi.fileAuthorized !== undefined && fi.fileAuthorized.includes(account);
        $("#foldertable").append(createSearchFileRow(fi, a || b, a, !a && !b));
    }
}

// 下载，删除，请求，授权
function createSearchFileRow(fi, aL, aD, aA, aR) {
    fi.fileName = html2Escape("(" + fi.distance + ")  " + fi.fileName);
    var fileRow = "<tr id=" + fi.fileId + " onclick='checkfile(event," + '"'
        + fi.fileId + '"' + ")' ondblclick='checkConsFile(event," + '"'
        + fi.fileId + '"' + ")' id='" + fi.fileId
        + "' class='filerow'><td>"
        + "<span class='btn btn-link btn-xs' onclick='showFileInfo(\"" + fi.fileId + "\")' data-toggle='modal' data-target='#folderInfoModal'>" + fi.fileId.split("-")[1] + "</span>"
        + "</td><td>"
        + fi.fileName
        + "</td><td class='hidden-xs'>" + fi.fileCreationDate + "</td>";
    if (fi.fileSize === "0") {
        fileRow = fileRow + "<td>&lt;1</td>";
    } else {
        fileRow = fileRow + "<td>" + fi.fileSize + "</td>";
    }
    fileRow = fileRow + "<td class='hidden-xs'>" + fi.fileCreator + "</td><td>";
    if (aL) {
        fileRow = fileRow
            + "<button onclick='showDownloadModel("
            + '"'
            + fi.fileId
            + '","'
            + replaceAllQuotationMarks(fi.fileName)
            + '"'
            + ")' class='btn btn-link btn-xs'><span class='glyphicon glyphicon-cloud-download'></span> 下载</button>";
    }
    if (aD && fi.fileCreator === account) {
        fileRow = fileRow
            + "<button onclick='showDeleteFileModel("
            + '"'
            + fi.fileId
            + '","'
            + replaceAllQuotationMarks(fi.fileName)
            + '"'
            + ")' class='btn btn-link btn-xs'><span class='glyphicon glyphicon-remove'></span> 删除</button>";
    }
    if (aA) {
        fileRow = fileRow
            + "<button id='ra-" + fi.fileId + "' onclick='showRequestAuthorizationModel("
            + '"'
            + fi.fileId
            + '","'
            + fi.fileCreator
            + '","'
            + replaceAllQuotationMarks(fi.fileName)
            + '"'
            + ")' class='btn btn-link btn-xs'><span class='glyphicon glyphicon-import'></span> 请求</button>";
    }
    if (aR) {
        fileRow = fileRow
            + "<button onclick='showResposeAuthorizationModel("
            + '"'
            + fi.fileId
            + '","'
            + replaceAllQuotationMarks(fi.fileName)
            + '"'
            + ")' class='btn btn-link btn-xs'><span class='glyphicon glyphicon-export'></span> 授权</button>";
    }
    fileRow = fileRow + "</td></tr>";
    return fileRow;
}

// 取消上传文件
function abortUpload_search() {
    if (isUpLoading) {
        isUpLoading = false;
        if (xhr != null) {
            xhr.abort();
        }
    }
    $("#hits-search")[0].value = '30'
    $("#uploadFileAlert-search").hide();
    $("#uploadFileSuccess-search").hide();
    $('#searchFileModal').modal('hide');
}

/*----------文件检索模块end----------*/

// 显示下载文件模态框
function showRequestAuthorizationModel(fileId, fileCreator, fileName) {
    $("#requestAuthorizationName").text("您确认要请求[" + fileCreator + "]授权文件：[" + fileName + "]么？");
    $("#requestAuthorizationBox")
        .html(
            "<button id='rabutton' type='button' class='btn btn-primary' onclick='doRequestAuthorization("
            + '"' + fileId + '"' + ")'>确认请求</button>");
    $("#rabutton").attr('disabled', false);
    $("#requestAuthorizationModal").modal('show');
}

function doRequestAuthorization(fileId) {
    $.ajax({
        type: "POST",
        dataType: "text",
        data: {
            fileId: fileId,
        },
        url: "homeController/requestAuthorization.ajax",
        success: function (result) {
            if (result === "permissiondenied") {
                window.location.href = "prv/login.html";
            } else {
                switch (result) {
                    case "success":
                        let a = $("#ra-" + fileId);
                        a.attr('disabled', true);
                        a.text("已请求");
                        $("#requestAuthorizationModal").modal('hide');
                        return;
                    default:
                        $("#requestAuthorizationName").text("请求文件出错了，以后再试");
                        break;
                }
            }
        }
    });
    $("#rabutton").attr('disabled', true);
}


function showResponseAuthorizationModel(fileId, requestAuthorizationOrgs) {
    let table = $("#responseAuthorizationTable");
    table.text("");
    var orgs = requestAuthorizationOrgs.split(",")
    for (let i = 0; i < orgs.length; i++) {
        table.append("<tr id='rpa-" + fileId + "'><th>" +
            orgs[i]
            + "</th><th>" +
            "<button id='rabutton' type='button' class='btn btn-success btn-xs' onclick='doResponseAuthorization("
            + '"' + fileId + '","' + orgs[i] + '"' + ")'>授权</button>"
            + "</th></tr>");
    }

    $("#responseAuthorizationModal").modal('show');
}

function doResponseAuthorization(fileId, organization) {
    $.ajax({
        type: "POST",
        dataType: "text",
        data: {
            fileId: fileId,
            organization: organization
        },
        url: "homeController/responseAuthorization.ajax",
        success: function (result) {
            if (result === "permissiondenied") {
                window.location.href = "prv/login.html";
            } else {
                let a = $("#rpa-" + fileId);
                switch (result) {
                    case "success":
                        a.remove();
                        break;
                    default:
                        a.text("授权文件出错了");
                        break;
                }
            }
        }
    });
}