# HMRC API definition. See [JSON definition](https://confluence.tools.tax.service.gov.uk/display/DTRG/JSON+definition)
Generated from [JSON schema](../app/resources/api-definition-schema.json)
## `root`
HMRC API definition. See [JSON definition](https://confluence.tools.tax.service.gov.uk/display/DTRG/JSON+definition)

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `api` | _object_ | Required | [api](#api) | Details of the API |
### `api`
Details of the API

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `name` | _string_ | Required | ^[A-Z]{1}.*$ | The name for the API (title case i.e. Individual Benefits, not Individual benefits). |
| `description` | _string_ | Required |  | The description for the API |
| `context` | _string_ | Required | ^[a-z]+[a-z/\-]{4,}$ | The unique context for the API. This should be consistent with the HMRC Domain Model. See [API Domain Model](https://confluence.tools.tax.service.gov.uk/display/ApiPlatform/HMRC%27s+API+Domain+Model) |
| `isTestSupport` | _boolean_ | Optional | False (default) | Categorises the API as being a Test Support API |
| `categories` | _string[]_ | Optional | EXAMPLE<br>AGENTS<br>BUSINESS_RATES<br>CHARITIES<br>CONSTRUCTION_INDUSTRY_SCHEME<br>CORPORATION_TAX<br>CUSTOMS<br>ESTATES<br>HELP_TO_SAVE<br>INCOME_TAX_MTD<br>LIFETIME_ISA<br>MARRIAGE_ALLOWANCE<br>NATIONAL_INSURANCE<br>PAYE<br>PENSIONS<br>PRIVATE_GOVERNMENT<br>RELIEF_AT_SOURCE<br>SELF_ASSESSMENT<br>STAMP_DUTY<br>TRUSTS<br>VAT<br>VAT_MTD<br>OTHER | The list of service groups the API will be categorised by. |
| `versions` | _object[]_ | Required | [versions](#versions) | A list of the different versions of the API |
### `versions`
Details of an API version

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `version` | _string_ | Required | ^[0-9\.P]+$ | The version number. Eg 1.0 |
| `status` | _string_ | Required | PROTOTYPED<br>PUBLISHED<br>ALPHA<br>BETA<br>STABLE<br>DEPRECATED<br>RETIRED | The current lifecycle status. PROTOTYPED and PUBLISHED should not be used. See [Lifecycle](https://confluence.tools.tax.service.gov.uk/x/iz6kB) |
| `endpoints` | _None_ | Optional |  | DEPRECATED |
| `endpointsEnabled` | _boolean_ | Optional | True (default) | Whether the endpoints are shown as available on the DevHub documentation page. This does not effect if the API can actually be used / called. This value MUST be false if the API versions status is ALPHA |
| `access` | _object_ | Optional | [access](#access) | Used to indicate whether this API version is public or private. If absent, the API defaults to public. |
| `fieldDefinitions` | _object[]_ | Optional | [fielddefinitions](#fieldDefinitions) | A list of subscription fields for this API version. |
### `access`
Used to indicate whether this API version is public or private. If absent, the API defaults to public.

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `type` | _string_ | Required | PUBLIC<br>PRIVATE | Whether the API version is publicly available or only for private use. |
| `whitelistedApplicationIds` | _string[]_ | Optional |  | DEPRECATED. This is no longer used. Please contact SDST to add applications to the allowlist. |
| `isTrial` | _boolean_ | Optional | False (default) | Whether this API version is a private trial |
### `fieldDefinitions`
Details a subscription field used by this API. If you would like to use subscription fields you should talk to the API Platform team first #team-api-platform-sup.

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `name` | _string_ | Required | ^[a-zA-Z]+$ | The internal identifier for this field |
| `description` | _string_ | Required |  | The description that will be shown to users for this field |
| `type` | _string_ | Required | URL<br>SecureToken<br>STRING<br>PPNSField | The type of value expected for this field |
| `hint` | _string_ | Optional |  | Hint text to display to users to help them provide a correct value for this field. If left blank the description will be used instead |
| `shortDescription` | _string_ | Optional |  | A short description that is displayed on the API metadata page |
| `validation` | _object_ | Optional | [validation](#validation) | Contains Rules to validate the value of the Field Definition. |
| `access` | _object_ | Optional | [access](#access-1) | Access control for the value of this Subscription Field |
### `validation`
Contains Rules to validate the value of the Field Definition.

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `errorMessage` | _string_ | Required |  | The error message that will be shown to users if this field is invalid. |
| `rules` | _object[]_ | Required | [rules](#rules) | An array of Validation Rules to validate the field's value. |
### `rules`
A Validation Rule to validate the field value.

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `RegexValidationRule` | _object_ | Optional | [regexvalidationrule](#RegexValidationRule) | A Regular Expression to validate the field value. |
| `UrlValidationRule` | _object_ | Optional | [urlvalidationrule](#UrlValidationRule) | This is an empty object to specify that URL Validation applies to the field's value. |
### `RegexValidationRule`
A Regular Expression to validate the field value.

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `regex` | _string_ | Required |  | A Regular Expression |
### `UrlValidationRule`
This is an empty object to specify that URL Validation applies to the field's value.

### `access`
Access control for the value of this Subscription Field

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `devhub` | _object_ | Optional | [devhub](#devhub) | Access control within Developer Hub for the value of this Subscription Field |
### `devhub`
Access control within Developer Hub for the value of this Subscription Field

| Name | Type | Required | Values | Description |
| --- | --- | --- | --- | --- |
| `read` | _string_ | Optional | anyone<br>adminOnly<br>noOne | Who has Read Access to the value of this Subscription Field |
| `write` | _string_ | Optional | anyone<br>adminOnly<br>noOne | Who has Write Access to the value of this Subscription Field |
