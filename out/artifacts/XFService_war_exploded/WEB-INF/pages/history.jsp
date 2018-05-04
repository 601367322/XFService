<%--
  Created by IntelliJ IDEA.
  User: bingbing
  Date: 2018/5/3
  Time: 下午1:58
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<html>
<head>
    <meta http-equiv="refresh" content="5">
    <title>语音转换</title>

    <style type="text/css">
        .title {
            font-size: 20px;
        }
    </style>

</head>
<body>
<a href="${pageContext.request.contextPath}/">返回首页，再转一个</a>
<H1>点击任意一项，进行下载</H1>
<ul>
    <c:forEach items="${files}" var="file">
        <c:if test="${file.value}">
            <a href="${pageContext.request.contextPath}/download?file=${file.key}">
                <li class="title">${file.key}(已完成)</li>
            </a>
        </c:if>
        <c:if test="${!file.value}">
            <li class="title">${file.key}(正在转换……)</li>
        </c:if>
    </c:forEach>
</ul>
</body>
</html>
