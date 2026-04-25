#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost}"
API_BASE="${BASE_URL%/}/api"
SUFFIX="${SUFFIX:-$(date +%s)}"
USERNAME="${USERNAME:-sf_user_${SUFFIX}}"
PASSWORD="${PASSWORD:-SuddenFix#123}"
PRODUCT_PRICE="${PRODUCT_PRICE:-19900}"
PRODUCT_STOCK="${PRODUCT_STOCK:-20}"

json_get() {
  local json="$1"
  local path="$2"
  printf '%s' "$json" | node -e '
const path = process.argv[1];
let raw = "";
process.stdin.on("data", (chunk) => {
  raw += chunk;
});
process.stdin.on("end", () => {
  try {
    const source = JSON.parse(raw);
    const value = path.split(".").reduce((acc, key) => (acc == null ? undefined : acc[key]), source);
    if (value === undefined || value === null) {
      process.exit(2);
    }
    process.stdout.write(typeof value === "object" ? JSON.stringify(value) : String(value));
  } catch (error) {
    process.exit(3);
  }
});
' "$path"
}

request_json() {
  local method="$1"
  local url="$2"
  local data="${3:-}"
  shift 3 || true
  local headers=("$@")

  if [[ -n "$data" ]]; then
    curl -sS -X "$method" "$url" \
      -H 'Content-Type: application/json' \
      "${headers[@]}" \
      -d "$data"
  else
    curl -sS -X "$method" "$url" "${headers[@]}"
  fi
}

request_form() {
  local method="$1"
  local url="$2"
  local data="$3"
  shift 3 || true
  local headers=("$@")
  curl -sS -X "$method" "$url" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    "${headers[@]}" \
    -d "$data"
}

assert_success() {
  local response="$1"
  local label="$2"
  local code
  code="$(json_get "$response" code || true)"
  if [[ "$code" != "200" ]]; then
    echo "[FAIL] $label"
    echo "$response"
    exit 1
  fi
}

echo "[1/10] 注册测试用户: $USERNAME"
REGISTER_PAYLOAD="$(cat <<JSON
{"username":"$USERNAME","password":"$PASSWORD","nickname":"UI Verify $SUFFIX"}
JSON
)"
REGISTER_RES="$(request_json POST "$API_BASE/user/register" "$REGISTER_PAYLOAD")"
assert_success "$REGISTER_RES" "用户注册"

echo "[2/10] 登录获取 Token"
LOGIN_PAYLOAD="$(cat <<JSON
{"account":"$USERNAME","password":"$PASSWORD"}
JSON
)"
LOGIN_RES="$(request_json POST "$API_BASE/user/login" "$LOGIN_PAYLOAD")"
assert_success "$LOGIN_RES" "用户登录"
TOKEN="$(json_get "$LOGIN_RES" data)"
AUTH_HEADER=(-H "Authorization: Bearer $TOKEN")

echo "[3/10] 创建联调用商品"
PRODUCT_PAYLOAD="$(cat <<JSON
{
  "name":"联调商品-$SUFFIX",
  "categoryId":1,
  "mainImage":"https://example.com/suddenfix-demo.png",
  "price":$PRODUCT_PRICE,
  "stock":$PRODUCT_STOCK,
  "description":"阶段五自动验证脚本创建的商品"
}
JSON
)"
PRODUCT_RES="$(request_json POST "$API_BASE/product/add" "$PRODUCT_PAYLOAD")"
assert_success "$PRODUCT_RES" "商品创建"
PRODUCT_ID="$(json_get "$PRODUCT_RES" data)"

echo "[4/10] 加入购物车，商品 ID: $PRODUCT_ID"
PREHEAT_RES="$(curl -sS -X POST "$API_BASE/product/preheat/$PRODUCT_ID")"
assert_success "$PREHEAT_RES" "商品预热"

echo "[5/10] 加入购物车，商品 ID: $PRODUCT_ID"
CART_ADD_PAYLOAD="$(cat <<JSON
{"productId":$PRODUCT_ID,"quantity":1}
JSON
)"
CART_ADD_RES="$(request_json POST "$API_BASE/order/cart/item" "$CART_ADD_PAYLOAD" "${AUTH_HEADER[@]}")"
assert_success "$CART_ADD_RES" "加入购物车"

echo "[6/10] 创建订单"
ORDER_PAYLOAD="$(cat <<JSON
{
  "idempotentKey":"verify-$SUFFIX",
  "products":{"$PRODUCT_ID":[1,$PRODUCT_PRICE]},
  "productNames":{"$PRODUCT_ID":"联调商品-$SUFFIX"},
  "freight":1200,
  "discountAmount":0,
  "receiverName":"联调收货人",
  "receiverPhone":"13800138000",
  "receiverAddress":"上海市浦东新区 SuddenFix 联调地址 1 号",
  "remark":"verify-flow.sh 自动下单",
  "payChannel":1
}
JSON
)"
ORDER_RES="$(request_json POST "$API_BASE/order/buy" "$ORDER_PAYLOAD" "${AUTH_HEADER[@]}")"
assert_success "$ORDER_RES" "创建订单"
ORDER_ID="$(json_get "$ORDER_RES" data)"

echo "[7/10] 轮询支付单生成，订单 ID: $ORDER_ID"
PAY_RECORD=""
for _ in {1..20}; do
  PAY_RES="$(curl -sS -X POST "$API_BASE/pay/mock/create?orderId=$ORDER_ID" "${AUTH_HEADER[@]}")"
  if [[ "$(json_get "$PAY_RES" code || true)" == "200" ]]; then
    PAY_RECORD="$PAY_RES"
    break
  fi
  sleep 2
done

if [[ -z "$PAY_RECORD" ]]; then
  echo "[FAIL] 支付单在预期时间内未生成"
  exit 1
fi

OUT_TRADE_NO="$(json_get "$PAY_RECORD" data.outTradeNo)"
echo "[8/10] 触发模拟支付回调: $OUT_TRADE_NO"
PAY_NOTIFY_RES="$(request_form POST "$API_BASE/pay/mock/notify" "outTradeNo=$OUT_TRADE_NO")"
if [[ "$PAY_NOTIFY_RES" != "success" ]]; then
  echo "[FAIL] 模拟支付回调失败"
  echo "$PAY_NOTIFY_RES"
  exit 1
fi

echo "[9/10] 等待订单状态变为已支付"
PAID_READY="false"
for _ in {1..20}; do
  ORDER_DETAIL_RES="$(curl -sS "$API_BASE/order/$ORDER_ID" "${AUTH_HEADER[@]}")"
  if [[ "$(json_get "$ORDER_DETAIL_RES" code || true)" == "200" ]] && [[ "$(json_get "$ORDER_DETAIL_RES" data.order.status || true)" == "20" ]]; then
    PAID_READY="true"
    break
  fi
  sleep 2
done

if [[ "$PAID_READY" != "true" ]]; then
  echo "[FAIL] 订单未在预期时间内变成已支付"
  echo "$ORDER_DETAIL_RES"
  exit 1
fi

echo "[10/10] 模拟发货"
SHIP_PAYLOAD="$(cat <<JSON
{
  "orderId":$ORDER_ID,
  "expressCompany":"SuddenFix Express",
  "remark":"verify-flow.sh 自动发货"
}
JSON
)"
SHIP_RES="$(request_json POST "$API_BASE/shipping/admin/ship" "$SHIP_PAYLOAD" "${AUTH_HEADER[@]}")"
assert_success "$SHIP_RES" "模拟发货"

echo "[11/11] 查询物流并模拟签收"
SHIPPING_RES="$(curl -sS "$API_BASE/shipping/order/$ORDER_ID" "${AUTH_HEADER[@]}")"
assert_success "$SHIPPING_RES" "查询物流"

COMPLETE_PAYLOAD="$(cat <<JSON
{"orderId":$ORDER_ID}
JSON
)"
COMPLETE_RES="$(request_json POST "$API_BASE/shipping/admin/complete" "$COMPLETE_PAYLOAD" "${AUTH_HEADER[@]}")"
assert_success "$COMPLETE_RES" "模拟签收"

echo
echo "验证完成"
echo "用户: $USERNAME"
echo "商品ID: $PRODUCT_ID"
echo "订单ID: $ORDER_ID"
echo "支付单号: $OUT_TRADE_NO"
