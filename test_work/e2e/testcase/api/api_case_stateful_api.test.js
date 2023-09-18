
const { ApiCommon, ExecSync, parseHttpResponse, getEnv } = require('../../common/test_common');


describe('Stateful API', () => {

  var hash = {};
  var transactionId;
  var downloadPathList;
  var fileNameList = [];

  baseURL = `${getEnv.getBaseURL()}/api`;

  expect.extend({
    toNullOrString(value) {
      const pass = (value === null || (typeof value) === 'string');
      if (pass) {
        return {
          message: () =>
            `expected null or String.`,
          pass: true,
        };
      } else {
        return {
          message: () =>
            `expected not null and not String.`,
          pass: false,
        };
      }
    },
  });


  beforeEach(() => {
    // ApiCommon.setEnableDebug();
    ApiCommon.login();
  });

  it('case start transaction', () => {
    const bodyParam = '{\\"type\\": \\"read_write\\", \\"timeoutMin\\": 1, \\"tables\\":[\\"demo\\"]}'
    const cmd = `curl -s -X POST -H 'Authorization: Bearer ${ApiCommon.getAccessToken()}' -H "Content-Type: application/json" -d "${bodyParam}" ${baseURL}/transaction/begin -i `;
    const ret = ExecSync(cmd);
    const [header, body] = parseHttpResponse(ret.toString());

    expect(body).toEqual(
      {
        type: 'read_write',
        transactionId: expect.anything(String),
        status: 'AVAILABLE',
        startTime: expect.toNullOrString(),
        endTime: null
      }
    );
    expect(header.indexOf('HTTP/1.1 200 OK')).toBeGreaterThan(-1);

    transactionId = body.transactionId;
  });

  it('case check transaction status', () => {
    const cmd2 = `curl -s -H 'Authorization: Bearer ${ApiCommon.getAccessToken()}' ${baseURL}/transaction/status/${transactionId} -i`;
    const ret2 = ExecSync(cmd2);
    const [header2, body2] = parseHttpResponse(ret2.toString());

    expect(body2).toEqual(
      {
        type: 'read_write',
        transactionId: transactionId,
        status: 'AVAILABLE',
        startTime: expect.toNullOrString(),
        endTime: null
      }
    );

    expect(header2.indexOf('HTTP/1.1 200 OK')).toBeGreaterThan(-1);
  });

  it('case start dump(Parquet)', () => {
    const cmd = `curl -s -X POST -H 'Authorization: Bearer ${ApiCommon.getAccessToken()}' ${baseURL}/transaction/dump/${transactionId}/demo -i`;
    const ret = ExecSync(cmd);
    const [header, body] = parseHttpResponse(ret.toString());

    expect(body).toEqual(
      {
        transactionId: transactionId,
        table: 'demo',
        format: 'parquet',
        downloadPathList: expect.any(Array)
      }
    );

    downloadPathList = body.downloadPathList;
    expect(header.indexOf('HTTP/1.1 200 OK')).toBeGreaterThan(-1);
  });

  it('case start dump(CSV)', () => {
    const cmd = `curl -s -X POST -H 'Authorization: Bearer ${ApiCommon.getAccessToken()}' -H "Content-Type: application/json" -d "{\\"format\\": \\"csv\\"}" ${baseURL}/transaction/dump/${transactionId}/demo -i`;
    const ret = ExecSync(cmd);
    const [header, body] = parseHttpResponse(ret.toString());
    console.log(body)

    expect(body).toEqual(
      {
        transactionId: transactionId,
        table: 'demo',
        format: 'csv',
        downloadPathList: expect.any(Array)
      }
    );

    downloadPathList = downloadPathList.concat(body.downloadPathList);
    expect(header.indexOf('HTTP/1.1 200 OK')).toBeGreaterThan(-1);
  });

  it('case download', () => {

    for (const path of downloadPathList) {
      console.log(path);
      const dlPath = path.replace('/', '%2F');
      const idx = path.lastIndexOf('/');
      const fileName = 'fs/dl/' + path.substring(idx + 1);
      console.log(dlPath);
      const cmd = `curl -s -H 'Authorization: Bearer ${ApiCommon.getAccessToken()}' ${baseURL}/download/${dlPath} -o ${fileName}`;
      const ret = ExecSync(cmd);
      fileNameList.push(fileName);
    }
  });

  it('case load(Parquet)', () => {

    const fileName = "fs/load_file/dump1.parquet";
    const cmd = `curl -s -X POST -H 'Authorization: Bearer ${ApiCommon.getAccessToken()}' ${baseURL}/transaction/load/${transactionId}/demo -i -F file=@${fileName}`;
    const ret = ExecSync(cmd);
    const [header, body] = parseHttpResponse(ret.toString());

    const idx = fileName.lastIndexOf('/');
    const path = transactionId + fileName.substring(idx);

    expect(body).toEqual(
      {
        transactionId: expect.anything(String),
        dumpFiles: [path],
        format: 'detect_by_ext',
        table: 'demo',
      }
    );
  });

  it('case load(CSV)', () => {

    const fileName = "fs/load_file/dump2.csv";
    const cmd = `curl -s -X POST -H 'Authorization: Bearer ${ApiCommon.getAccessToken()}' ${baseURL}/transaction/load/${transactionId}/demo -i -F file=@${fileName}`;
    const ret = ExecSync(cmd);
    const [header, body] = parseHttpResponse(ret.toString());

    const idx = fileName.lastIndexOf('/');
    const path = transactionId + fileName.substring(idx);

    expect(body).toEqual(
      {
        transactionId: expect.anything(String),
        dumpFiles: [path],
        format: 'detect_by_ext',
        table: 'demo',
      }
    );
  });

  it('case commit', () => {
    const cmd = `curl -s -X POST -H 'Authorization: Bearer ${ApiCommon.getAccessToken()}' ${baseURL}/transaction/commit/${transactionId} -i`;
    const ret = ExecSync(cmd);
    const [header, body] = parseHttpResponse(ret.toString());

    expect(body).toEqual(
      {
        type: 'read_write',
        transactionId: expect.anything(String),
        status: 'COMMITED',
        startTime: expect.toNullOrString(),
        endTime: expect.toNullOrString(),
      }
    );

    expect(header.indexOf('HTTP/1.1 200 OK')).toBeGreaterThan(-1);
  });

});
