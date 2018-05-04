<%--
  Created by IntelliJ IDEA.
  User: bingbing
  Date: 2018/5/3
  Time: 下午1:58
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>语音转换</title>
</head>
<body>
<script type="text/javascript">
    function validateForm() {
        var file = document.getElementById("file");
        if (file.value.length == 0)
            return false;
        return true;
    }
</script>
<form method="post" action="${pageContext.request.contextPath}/do" onsubmit="return validateForm();" enctype="multipart/form-data">
    <input type="file" multiple="true" name="file" id="file"/>
    <input type="submit" value="提交">
</form>
<a href="${pageContext.request.contextPath}/history"><div>查看历史</div></a>
</body>
</html>
