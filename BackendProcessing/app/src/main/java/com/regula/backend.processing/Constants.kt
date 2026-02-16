package com.regula.backend.processing
object Constants {
    //const val SETTINGS_PASSWORD = "v9J#2kLm!7qPz4sT@8wX"
    const val SETTINGS_PASSWORD = "p1"
    const val ENDPOINT_IPROOV_CREATE_ENROLLMENT_TOKEN = "/iproov/create-enrollment-token"
    const val ENDPOINT_IPROOV_ENROLL_PHOTO = "/iproov/enroll-photo"
    const val ENDPOINT_IPROOV_CREATE_VERIFY_TOKEN = "/iproov/create-verify-token"
    const val ENDPOINT_IPROOV_VALIDATE_VERIFICATION = "/iproov/validate-verification"
    const val ENDPOINT_REGISTRATION_MFA_INITIATE_EMAIL = "/registration/mfa/initiate/email"
    const val ENDPOINT_REGISTRATION_MFA_VERIFY_EMAIL = "/registration/mfa/verify/email"
    const val ENDPOINT_REGISTRATION_MFA_INITIATE_SMS = "/registration/mfa/initiate/sms"
    const val ENDPOINT_REGISTRATION_MFA_VERIFY_SMS = "/registration/mfa/verify/sms"
    const val ENDPOINT_ABIS_ENROLL_VOTER = "/abis/enroll-voter"
    const val ENDPOINT_ADD_ENCOUNTER = "/abis/add-encounter"
    const val REGULA_BASE_URL = "https://api.regulaforensics.com"
    const val IPROOV_BASE_URL = "wss://sg.rp.secure.iproov.me/ws"
    const val FUEL_URL = "https://sg.rp.secure.iproov.me/api/v2/"
    const val API_KEY = "8a22f4928c1191a8f1c21ed8b8ca4871fc5b4fb7"
    const val SECRET = "0cdeeca2ae00d68fb25e0c0aeacf63c61d3c0f44"
    //const val NEUVOTE_SERVER_ADDRESS = "http://192.168.0.228" //local
    //const val NEUVOTE_SERVER_PORT = "3001"                    //local
    //const val NEUVOTE_SERVER_ADDRESS = "http://biometrics-demo-env.eba-tphgd5hp.ca-central-1.elasticbeanstalk.com"    //http
    const val NEUVOTE_SERVER_ADDRESS = "https://api.biometrics.demo.civik.ca"                                           //https
    const val NEUVOTE_SERVER_PORT = ""
    const val BIOMETRICS_APP_PACKAGE = "com.neurotec.samples.neuvotemultibiometric"
    const val MOBILE_CONFIG = true
}
