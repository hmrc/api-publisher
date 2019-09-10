# HMRC API definition. See [JSON definition]
Generated from [JSON schema](app/resources/api-definition-schema.json)
## `root`
HMRC API definition. See [JSON definition]

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `scopes` | _object[]_ | Required | [scopes](#scopes) | A list of all OAuth scopes used by this API |
| `api` | _object_ | Required | [api](#api) | Details of the API |
### `scopes`
Details of an OAuth scope

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `confidenceLevel` | _integer_ | Optional |  | The Identity Verification confidence level required to use this scope. Value can be 50, 100, 200 or 300. See [Identity Verification on the API Platform](https://confluence.tools.tax.service.gov.uk/display/DTRG/Identity+Verification+on+the+API+Platform) |
| `name` | _string_ | Required |  | The human friendly name for this scope (sentence case). This is shown to users in the OAuth journey and in documentation, |
| `key` | _string_ | Required | ^[a-z:\-0-9]+$ | The key/identifier used to refer to this scope (lowercase, separate words with a colon(:)). |
| `description` | _string_ | Required |  | The description for this scope (recommendation set this to the same value as the name) |
### `api`
Details of the API

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `name` | _string_ | Required | ^[A-Z]{1}.*$ | The name for the API (title case i.e. Individual Benefits, not Individual benefits). |
| `versions` | _object[]_ | Required | [versions](#versions) | A list of the different versions of the API |
| `requiresTrust` | _boolean_ | Optional | False (default) | DEPRECATED. This has been superseded by Private API access |
| `context` | _string_ | Required | ^[a-z]+[a-z/\-]{4,}$ | The unique context for the API. This should be consistent with the HMRC Domain Model. See [API Domain Model](https://confluence.tools.tax.service.gov.uk/display/ApiPlatform/HMRC%27s+API+Domain+Model) |
| `isTestSupport` | _boolean_ | Optional | False (default) | Categorises the API as being a Test Support API |
| `categories` | _string[]_ | Optional | EXAMPLE<br>AGENTS<br>BUSINESS_RATES<br>CHARITIES<br>CONSTRUCTION_INDUSTRY_SCHEME<br>CORPORATION_TAX<br>CUSTOMS<br>ESTATES<br>HELP_TO_SAVE<br>INCOME_TAX_MTD<br>LIFETIME_ISA<br>MARRIAGE_ALLOWANCE<br>NATIONAL_INSURANCE<br>PAYE<br>PENSIONS<br>PRIVATE_GOVERNMENT<br>RELIEF_AT_SOURCE<br>SELF_ASSESSMENT<br>STAMP_DUTY<br>TRUSTS<br>VAT<br>VAT_MTD<br>OTHER | The list of service groups the API will be categorised by. |
| `description` | _string_ | Required |  | The description for the API |
### `versions`
Details of an API version

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `status` | _string_ | Required | PROTOTYPED<br>PUBLISHED<br>ALPHA<br>BETA<br>STABLE<br>DEPRECATED<br>RETIRED | The current lifecycle status. PROTOTYPED and PUBLISHED should not be used. See [Lifecycle] |
| `fieldDefinitions` | _object[]_ | Optional | [fielddefinitions](#fieldDefinitions) | A list of subscription fields for this API version. |
| `access` | _object_ | Optional | [access](#access) | Used to indicate whether this API version is public or private. If absent, the API defaults to public. See [Access] |
| `endpointsEnabled` | _boolean_ | Optional | True (default) | Whether the endpoints are enabled. This value MUST be false if the API versions status is ALPHA |
| `version` | _string_ | Required | ^[0-9\.P]+$ | The version number. Eg 1.0 |
| `endpoints` | _None_ | Optional |  | DEPRECATED |
### `fieldDefinitions`
Details a subscription field used by this API. If you would like to use subscription fields you should talk to the API Platform team first #team-api-platform-sup.

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `hint` | _string_ | Optional |  | Hint text to display to users to help them provide a correct value for this field |
| `type` | _string_ | Required | URL<br>SecureToken<br>STRING | The type of value expected for this field |
| `name` | _string_ | Required | ^[a-zA-Z]*$ | The internal identifier for this field |
| `description` | _string_ | Required |  | The description that will be shown to users for this field |
### `access`
Used to indicate whether this API version is public or private. If absent, the API defaults to public. See [Access]

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `whitelistedApplicationIds` | _string[]_ | Optional |  | A list of Developer Hub Application IDs that are whitelisted to access this Private API version |
| `isTrial` | _boolean_ | Optional |  | Whether this API version is a private trial |
| `type` | _string_ | Required | PUBLIC<br>PRIVATE | Whether the API version is publicly available or only for private use. |
