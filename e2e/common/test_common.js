const { execSync } = require("child_process");
require('dotenv').config();

module.exports.parseHttpResponse = (response) => {

  let header = [];
  let body = '';
  let isHeader = true;
  for (let v2 of response.split("\r\n")) {
    if (v2.trim() === '') {
      isHeader = false;
    }
    if (isHeader)
      header.push(v2);
    else
      body = v2;
  }

  if (this.isEnableDebug) console.debug("header", header);
  if (this.isEnableDebug) console.debug("header", body);

  return [header, JSON.parse(body)];
}

module.exports.ExecSync = (cmd) => {
  if (ApiCommon.isEnableDebug) {
    console.info('ExecSync cmd:' + cmd);
  }
  return execSync(cmd);
}

module.exports.ExecSyncIO = (cmd) => {
  if (ApiCommon.isEnableDebug) {
    console.info('ExecSync cmd:' + cmd);
  }
  return execSync(cmd, {stdio: 'inherit'});
}

class getEnv {
  static getBaseURL() {
    return `${process.env.BELAYER_API_HOST}`;
  }
  static getCLIPath() {
    return `${process.env.BELAYER_CLI_PATH}`;
  }
}

module.exports.getEnv = getEnv;

class ApiCommon {

  static accessToken = "";
  static isEnableDebug = false;

  static login() {
    this.accessToken = this._login(`${getEnv.getBaseURL()}/api`);
  }

  static setEnableDebug() {
    this.isEnableDebug = true;
  }

  static getAccessToken() {
    return this.accessToken;
  }

  static _login(baseURL) {
    const loginCmd = `curl -s -X POST --data-urlencode 'uid=tsurugi' -d 'pw=password' ${baseURL}/auth`;
    if (ApiCommon.isEnableDebug) {
      console.info('login:' + loginCmd);
    }
    try {
      var res = execSync(loginCmd);
      var obj = JSON.parse(res.toString());
    } catch (e) {
      console.log(e);
    }
    if (ApiCommon.isEnableDebug) {
      console.info('get accessToken:' + obj.accessToken);
    }
    return obj.accessToken;
  }
}

module.exports.ApiCommon = ApiCommon;
