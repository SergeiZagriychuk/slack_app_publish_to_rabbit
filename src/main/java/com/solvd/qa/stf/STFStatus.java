package com.solvd.qa.stf;

import org.apache.commons.lang3.StringUtils;

import io.restassured.RestAssured;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class STFStatus {

	private final static String STF_URL = "STF_URL_";
	private final static String STF_TOKEN = "STF_TOKEN_";
	private final static String DEVICES_PATH = "/api/v1/devices";
	private final static String AUTH_HEADER = "Authorization";
	private final static String AUTH_TOKEN_TYPE = "Bearer";

	private final static String PATH_IOS_AVAILABLE = "devices.findAll {it.platform == 'iphoneos' && it.present == true && it.owner == null}.model";
	private final static String PATH_IOS_TOTAL = "devices.findAll {it.platform == 'iphoneos'}.model";
	private final static String PATH_ANDROID_AVAILABLE = "devices.findAll {it.platform == 'Android' && it.present == true && it.owner == null}.model";
	private final static String PATH_ANDROID_TOTAL = "devices.findAll {it.platform == 'Android'}.model";

	private final static String STATUS_SUCCESS = "%d of %d";
	private final static String STATUS_UNDEFINED = "undefined";

	/**
	 * Returns {status_android, status_ios}
	 * 
	 * @param tenant valid tenant name
	 * @return {status_android, status_ios}
	 */
	public static String[] getStfDevicesStatus(String tenant) {
		String url = System.getenv(STF_URL.concat(tenant));
		String token = System.getenv(STF_TOKEN.concat(tenant));
		if (StringUtils.isEmpty(url) || StringUtils.isEmpty(token)) {
			log.warn("STF credentials are not defined for tenant " + tenant);
			return new String[] { STATUS_UNDEFINED, STATUS_UNDEFINED };
		}
		log.debug("STF data was found for tenant " + tenant);

		try {
			ExtractableResponse<Response> rs = RestAssured.given().with()
					.header(AUTH_HEADER, AUTH_TOKEN_TYPE.concat(" ").concat(token)).get(url.concat(DEVICES_PATH)).then()
					.statusCode(200).extract();

			long iosAvailable = rs.jsonPath().getList(PATH_IOS_AVAILABLE).size();
			long iosTotal = rs.jsonPath().getList(PATH_IOS_TOTAL).size();
			long androidAvailable = rs.jsonPath().getList(PATH_ANDROID_AVAILABLE).size();
			long androidTotal = rs.jsonPath().getList(PATH_ANDROID_TOTAL).size();

			if (iosTotal + androidTotal == 0) {
				return new String[] { STATUS_UNDEFINED, STATUS_UNDEFINED };
			} else {
				return new String[] { String.format(STATUS_SUCCESS, androidAvailable, androidTotal),
						String.format(STATUS_SUCCESS, iosAvailable, iosTotal) };
			}
		} catch (Throwable e) {
			log.warn("Exception during obtaining devices status in STF", e);
			return new String[] { STATUS_UNDEFINED, STATUS_UNDEFINED };
		}
	}

	public static void main(String[] args) {
		System.err.println(getStfDevicesStatus("ua-ecomm"));
	}

}