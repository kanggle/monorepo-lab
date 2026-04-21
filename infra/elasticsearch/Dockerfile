# Elasticsearch with Korean morphological analyzer (nori).
#
# 공식 elasticsearch:8.15.0 이미지를 기반으로 analysis-nori 플러그인을 사전 설치한다.
# standard analyzer는 공백/구두점 토큰화만 지원해 한국어 부분 매칭이 불가능하다
# (예: "바지" 쿼리로 "청바지" 매칭 실패). nori는 형태소 분석 + 복합명사 분해로
# 이 문제를 해결한다. 자세한 의사결정 배경은 docs/adr/005-korean-search-analyzer.md 참고.
FROM elasticsearch:8.15.0

RUN elasticsearch-plugin install --batch analysis-nori
