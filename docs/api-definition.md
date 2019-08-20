Generated from [JSON schema](app/resources/api-definition-schema.json)
## `root`
HMRC API definition

| key | type | required | values | default | description
| --- | --- | --- | --- | --- | --- |
| `scopes` | `object[]` | Required | [scopes](#scopes) |  | A list of OAuth scopes used by this API
| `api` | `object` | Required | [api](#api) |  | The definition of the API
### `scopes`


| key | type | required | values | default | description
| --- | --- | --- | --- | --- | --- |
| `confidenceLevel` | `integer` | Optional |  |  | The Identity Verification confidence level required to use this scope. Value can be 50, 100, 200 or 300
| `name` | `string` | Required |  |  | The human friendly name for this scope shown to users in the OAuth journey and in documentation
| `key` | `string` | Required |  |  | The key/identifier used to refer to this scope
| `description` | `string` | Required |  |  | The description for this scope (recommendation set this to the same value as the name)
### `api`
The definition of the API

| key | type | required | values | default | description
| --- | --- | --- | --- | --- | --- |
| `name` | `string` | Required |  |  | The name for the API
| `versions` | `object[]` | Required | [versions](#versions) |  | A list of the different versions of the API
| `requiresTrust` | `boolean` | Optional |  | False | DEPRECATED. This has been superseded by Private APIs
| `context` | `string` | Required |  |  | The unique context for the API. This should be consistent with the HMRC Domain Model
| `isTestSupport` | `boolean` | Optional |  | False | Categorises the API as being a Test Support API
| `categories` | `string[]` | Optional | EXAMPLE<br>AGENTS<br>BUSINESS_RATES<br>CHARITIES<br>CONSTRUCTION_INDUSTRY_SCHEME<br>CORPORATION_TAX<br>CUSTOMS<br>ESTATES<br>HELP_TO_SAVE<br>INCOME_TAX_MTD<br>LIFETIME_ISA<br>MARRIAGE_ALLOWANCE<br>NATIONAL_INSURANCE<br>PAYE<br>PENSIONS<br>PRIVATE_GOVERNMENT<br>RELIEF_AT_SOURCE<br>SELF_ASSESSMENT<br>STAMP_DUTY<br>TRUSTS<br>VAT<br>VAT_MTD<br>OTHER |  | The list of service groups the API will appear under.
| `description` | `string` | Required |  |  | The description for the API
### `versions`


| key | type | required | values | default | description
| --- | --- | --- | --- | --- | --- |
| `status` | `string` | Required | PROTOTYPED<br>PUBLISHED<br>ALPHA<br>BETA<br>STABLE<br>DEPRECATED<br>RETIRED |  | The current lifecycle status. PROTOTYPED and PUBLISHED should not be used
| `endpointsEnabled` | `boolean` | Optional |  | True | Whether the endpoints are enabled. This value MUST be false if the API versions status is ALPHA
| `version` | `string` | Required |  |  | The version number
| `fieldDefinitions` | `object[]` | Optional | [fielddefinitions](#fieldDefinitions) |  | A list of subscription fields for this API version
| `access` | `object` | Optional | [access](#access) |  | Used to indicate whether this API version is public or private. If absent, the API defaults to public
### `fieldDefinitions`


| key | type | required | values | default | description
| --- | --- | --- | --- | --- | --- |
| `hint` | `string` | Optional |  |  | Hint text to display to users to help them provide a correct value for this field
| `type` | `string` | Required | URL<br>SecureToken<br>STRING |  | The type of value expected for this field
| `name` | `string` | Required |  |  | The internal identifier for this field
| `description` | `string` | Required |  |  | The description that will be shown to users for this field
### `access`
Used to indicate whether this API version is public or private. If absent, the API defaults to public

| key | type | required | values | default | description
| --- | --- | --- | --- | --- | --- |
| `whitelistedApplicationIds` | `string[]` | Optional |  |  | Application IDs that are whitelisted to access this PRIVATE API version
| `isTrial` | `boolean` | Optional |  |  | Whether this API version is a private trial
| `type` | `string` | Required | PUBLIC<br>PRIVATE |  | Whether the API version is publicly available or only for private use.
