
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ include file="/WEB-INF/_include/taglib.jsp"%>
<main id="content" class="safety-page">
  <div class="container">
    <section class="hdSection">
      <!-- tit-wrap -->
      <div class="tit-wrap">
        <div class="tit-left">
          <h1 class="heading1"><spring:message code="sft.sft_0600.label.title.list" /></h1>
        </div>
      </div>
      <!-- //tit-wrap -->
    </section>

    <!-- D : contSection border /
      1. tab : border (If you have a tap, please add this.)
      2. no tab : unborder  add (border-top disappears.)
       -->
    <section class="contSection unborder">
      <div class="content">
        <div class="content-body">

          <!-- fixed-search-form -->
          <div class="fixed-search-form">
            <div class="flex-wrap">
              <!-- item -->
              <!-- <div class="item">
                <span class="item-tit"><spring:message
                  code="sft.sft_0600.label.payment_status" /></span>
                <div class="select-group">
                  <select id="SEARCH_PAYMENT_STATUS" name="SEARCH_PAYMENT_STATUS" title="발주처">
                    <option value="">전체</option>
                  </select>
                </div>
              </div> -->
              <!-- item -->
              <div class="item search">
                <span class="item-tit"><spring:message code="txt.search" /></span>
                <div class="register-write">
                  <div class="input-group">
                    <input id="SEARCH_CRITERIA_ALL" name="SEARCH_CRITERIA_ALL" type="text" title="통합검색" placeholder="<spring:message code="txt.search.placeholder" />">
                  </div>
                </div>
              </div>
            </div>
            <div class="btn-wrap">
              <button class="refresh-btn" id="SEARCH_RESET_BTN"><span class="sr-only">새로고침</span></button>
                <button class="search-btn" id="SEARCH_BTN"><spring:message code="common.button.label.search"/></button>
            </div>
          </div>
          <!-- // fixed-search-form -->

          <!-- D : list-form (목록폼)
            1. After setting the width value on the colgroup,
            2. Please cover all the text in td with a <p> tag (ellipsis) -->
          <article class="list-form">
            <!-- table -->
            <div class="base-table center-table">
              <table>
                <caption></caption>
                <colgroup>
                  <col style="width: 7%;">
                  <col style="width: 25%;">
                  <col style="width: 20%;">
                  <col style="width: 25%;">
                  <col style="width: 25%;">
                </colgroup>
                <thead>
                  <tr>
                    <th scope="col"><spring:message code="hea.label.no"/></th>
                    <th scope="col" class="txt-left"><spring:message code="sft.sft_0600.label.Project_name" /></th>
                    <th scope="col">문서번호</th>
                    <th scope="col"><spring:message code="sft.sft_0600.label.Invester" /></th>
                    <th scope="col"><spring:message code="sft.sft_0600.label.Organization_chart" /></th>
                  </tr>
                </thead>
                <tbody id="ROW_LIST">

                  
                </tbody>

                <!-- last tr bottom shadow -->
                <tfoot>
                  <tr class="hidden-table-bottom">
                    <td></td>
                  </tr>
                </tfoot>
                <!-- last tr bottom shadow -->
              </table>
            </div>
            <!-- //table -->

            <!-- table-foot-area -->
            <div class="table-foot-area">
              <!-- 정렬 -->
              <div class="sort-length">
                <div class="select-group">
                  <select class="select" title="한 페이지에 표시할 행 설정" id="PAGE_SIZE">
                    <option value="10" selected>10
											<spring:message code="pcs.keyword" /></option>
										<option value="20">20
											<spring:message code="pcs.keyword" /></option>
										<option value="30">30
											<spring:message code="pcs.keyword" /></option>
                  </select>
                </div>
              </div>
              <!-- pager -->
              <div id="PAGENATION" class="pager">
              <a href="javascript:void(0);" id="PRE_PAGE" class="arr prev">
                <span class="sr-only">이전</span>
              </a> <a href="javascript:void(0);" id="NXT_PAGE" class="arr next">
                <span class="sr-only">다음</span>
              </a>
              </div>
              <!-- foot btn -->
              <div class="foot-btn">
                <button id="REGISTER_BTN" class="btn-style1">
                  <i class="las la-edit"></i><span class="name"><spring:message code="button.register"/></span>
                </button>
              </div>
            </div>
            <!-- //table-foot-area -->
          </article>
        </div>

      </div>
    </section>
  </div>
</main>
<script src="${ctxPath}/script/safety/safety_0600.js"></script>
<script src="${ctxPath}/script/sys/sys-common.js"></script>
<script src="${ctxPath}/script/sys/sys-element.js"></script>
<script>
  var page = 1;
	$(document).ready(function(){
    var localParam = retrieveLocalObject('paramSearch');
    if(localParam != null){
        page = localParam['PAGE'] != null ? localParam['PAGE'] : 1;
        const search_all = localParam['all'];
        const search_payment_status = localParam['SEARCH_INVESTOR'];        
        var pageSize = localParam['PAGE_SIZE'] != null ? localParam['PAGE_SIZE'] : "10";
        
        $('select#PAGE_SIZE').val(pageSize);
        $('select#SEARCH_PAYMENT_STATUS').val(search_payment_status);
        $('input#SEARCH_CRITERIA_ALL').val(search_all);
        _search.ALL =  search_all;
        _search.A= search_payment_status;        
    } 
		safety_0600();
	});
</script>