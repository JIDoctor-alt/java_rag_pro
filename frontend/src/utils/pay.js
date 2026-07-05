/** 是否微信内置浏览器 */
export function isWeChatBrowser() {
  return /MicroMessenger/i.test(navigator.userAgent)
}

/** 从 URL / localStorage 读取 openId */
export function getStoredOpenId() {
  const params = new URLSearchParams(window.location.search)
  const fromUrl = params.get('openid')
  if (fromUrl) {
    localStorage.setItem('wechat_openid', fromUrl)
    return fromUrl
  }
  return localStorage.getItem('wechat_openid') || ''
}

/** 公众号 JSAPI 调起支付 */
export function invokeWeChatJsapiPay(params) {
  return new Promise((resolve, reject) => {
    const payReq = {
      appId: params.appId,
      timeStamp: params.timeStamp,
      nonceStr: params.nonceStr,
      package: params.packageValue,
      signType: params.signType || 'RSA',
      paySign: params.paySign
    }

    function onBridgeReady() {
      window.WeixinJSBridge.invoke('getBrandWCPayRequest', payReq, (res) => {
        if (res.err_msg === 'get_brand_wcpay_request:ok') {
          resolve(res)
        } else {
          reject(new Error(res.err_msg || '微信支付失败'))
        }
      })
    }

    if (typeof window.WeixinJSBridge === 'undefined') {
      document.addEventListener('WeixinJSBridgeReady', onBridgeReady, false)
    } else {
      onBridgeReady()
    }
  })
}

/** 提交支付宝 H5 表单 */
export function submitAlipayForm(html) {
  const container = document.createElement('div')
  container.innerHTML = html
  document.body.appendChild(container)
  const form = container.querySelector('form')
  if (form) {
    form.submit()
    return
  }
  throw new Error('无效的支付宝表单')
}
