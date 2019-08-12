## Root
API definition

| key | type | required | description | values
| --- | --- | --- | --- | --- |
| [scopes](#scopes) | object[] | Optional | The OAuth scopes used by this API | 
| [api](#api) | object | Required |  | 
## scopes


| key | type | required | description | values
| --- | --- | --- | --- | --- |
| confidenceLevel | integer | Optional | The confidence level required to use this scope. Value can be 50, 100, 200 or 300 | 
| name | string | Required | The name for this scope shown to users in the OAuth journey | 
| key | string | Required | The key used to refer to this scope | 
| description | string | Required | The description for this scope | 
## api


| key | type | required | description | values
| --- | --- | --- | --- | --- |
| name | string | Required | The name for the API | 
| [versions](#versions) | object[] | Required | The versions that the API supports | 
| requiresTrust | boolean | Optional | DEPRECATED. This has been superseded by Private APIs | 
| context | string | Required | The unique context for the API. This should be consistent with the HMRC Domain Model | 
| isTestSupport | boolean | Optional | Categorises the API as being a Test Support API | 
| categories | string[] | Optional | The list of service groups the API will appear under. | EXAMPLE<br>AGENTS<br>BUSINESS_RATES<br>CHARITIES<br>CONSTRUCTION_INDUSTRY_SCHEME<br>CORPORATION_TAX<br>CUSTOMS<br>ESTATES<br>HELP_TO_SAVE<br>INCOME_TAX_MTD<br>LIFETIME_ISA<br>MARRIAGE_ALLOWANCE<br>NATIONAL_INSURANCE<br>PAYE<br>PENSIONS<br>PRIVATE_GOVERNMENT<br>RELIEF_AT_SOURCE<br>SELF_ASSESSMENT<br>STAMP_DUTY<br>TRUSTS<br>VAT<br>VAT_MTD<br>OTHER
| description | string | Required | The description for the API | 
## versions


| key | type | required | description | values
| --- | --- | --- | --- | --- |
| status | string | Required | The current lifecycle status for this API version. PROTOTYPED and PUBLISHED should not be used | PROTOTYPED<br>PUBLISHED<br>ALPHA<br>BETA<br>STABLE<br>DEPRECATED<br>RETIRED
| endpointsEnabled | boolean | Optional | Whether the endpoints for this API version are available to call | 
| version | string | Required | The version number of the API | 
| [fielddefinitions](#fielddefinitions) | object[] | Optional | List of subscription fields for this API version | 
| [access](#access) | object | Optional | Used to indicate whether this API version is public or private. If absent, the API defaults to public | 
## fieldDefinitions


| key | type | required | description | values
| --- | --- | --- | --- | --- |
| hint | string | Optional | Hint text to display to users to help them provide a correct value for this field | 
| type | string | Required | The type of value expected for this field | 
| name | string | Required | The internal identifier for this field | 
| description | string | Required | The description that will be shown to users for this field | 
## access
Used to indicate whether this API version is public or private. If absent, the API defaults to public

| key | type | required | description | values
| --- | --- | --- | --- | --- |
| whitelistedApplicationIds | string[] | Optional | IDs of the applications that are whitelisted to access this API version | 
| isTrial | boolean | Optional | Whether this API version is a private trial | 
| type | string | Required | The access type for this API version. | PUBLIC<br>PRIVATE
