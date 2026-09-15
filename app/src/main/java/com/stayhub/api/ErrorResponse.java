package com.stayhub.api;

/** 우리 API 자체의 오류 응답
 * 잘못된 요청(400)에 사용
 * 공급사 쪽 실패는 여기가 아니라 SearchResponse.suppliers에 */
public record ErrorResponse(String error, String message) {
}
