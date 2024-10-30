#!/usr/bin/env python

import json
import argparse
#
# Generates documentation from a JSON schema
# Usage example
# `python generate-api-definition-docs.py app/resources/api-definition-schema.json > docs/api-definition.md`
# Will generate markdown and output to STDOUT
parser = argparse.ArgumentParser(description='Generate documentation from a JSON schema')
parser.add_argument('schema_file', metavar='FILE', help='JSON file containing the JSON schema')
parser.add_argument('-v', '--verbose', action='store_true', help='Enable verbose output')

args = parser.parse_args()
anchors = set()

def output(text):
    print(text)
    pass


def output_row(name, definition, is_required):
    """
    Output a table row markdown detailing information about JSON key
    :param name: Name of the JSON key
    :param definition: dict, the JSON schema definition for the key
    :param is_required: Is the key Optional or Required
    :return: None
    """
    data_type = definition.get('type')
    enum = definition.get('enum')
    default = definition.get('default', '')
    values = ''

    # If the data type is an array get details of the type within the array
    if data_type == 'array':
        data_type = definition.get('items').get('type') + '[]'
        if definition.get('items').get('type') == 'string':
            enum = definition.get('items').get('enum')

    # if the data type is an object then make it into a link
    if data_type == 'object' or data_type == 'object[]':
        anchor = name + "-1" if name in anchors else name
        values = '[{}](#{})'.format(name.lower(), anchor)
        anchors.add(anchor)

    # if the data type is a string with a pattern then use the regex pattern as the value
    if data_type == 'string' and definition.get('pattern'):
        values = definition.get('pattern')

    if definition.get('default') is not None:
        values = '{} (default)'.format(definition.get('default'))

    # Separate lists with a <br>
    if isinstance(enum, list):
        values = '<br>'.join(enum)

    output(
        '| `{}` | _{}_ | {} | {} | {} |'.format(
            name,
            data_type,
            is_required,
            values,
            definition.get('description', '')
        )
    )


def output_object(name, schema_object, **kwargs):
    """
    Output the markdown for a JSON schema object
    :param name: Name of the object/key
    :param schema_object: JSON schema definition
    :return: None
    """
    required = schema_object.get('required', [])
    children = []

    output('{} `{}`'.format('#' * kwargs.get('level', 2), name))
    output(schema_object.get('description', ''))
    output('')
    properties = schema_object.get('properties', [])
    if len(properties) != 0:
        output(
            '| {} | {} | {} | {} | {} |'.format('Name', 'Type', 'Required', 'Values', 'Description')
        )
        output('| --- | --- | --- | --- | --- |')
        for name, definition in properties.items():
            is_required = 'Required' if name in required else 'Optional'
            if definition.get('type') == 'object':
                children.append({'name': name, 'definition': definition})
            if definition.get('type') == 'array' and definition.get('items').get('type') == 'object':
                children.append({'name': name, 'definition': definition.get('items')})
            output_row(name, definition, is_required)

        for child in children:
            output_object(child['name'], child['definition'], level=3)


with open(args.schema_file) as file:
    schema = json.load(file)

output('# {}'.format(schema.get('description')))
output('Generated from [JSON schema]({})'.format(args.schema_file))
output_object('root', schema)
