/*
 * Copyright 2019 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.abi.datatypes;

import org.abi.Utils;
import org.abi.datatypes.AbiTypes;
import org.abi.datatypes.Array;
import org.abi.datatypes.StructType;
import org.abi.datatypes.Type;

import java.util.List;

/** Dynamic array type. */
public class DynamicArray<T extends Type> extends Array<T> {

    @Deprecated
    @SafeVarargs
    @SuppressWarnings({"unchecked"})
    public DynamicArray(T... values) {
        super(
                org.abi.datatypes.StructType.class.isAssignableFrom(values[0].getClass())
                        ? (Class<T>) values[0].getClass()
                        : (Class<T>) org.abi.datatypes.AbiTypes.getType(values[0].getTypeAsString()),
                values);
    }

    @Deprecated
    @SuppressWarnings("unchecked")
    public DynamicArray(List<T> values) {
        super(
                org.abi.datatypes.StructType.class.isAssignableFrom(values.get(0).getClass())
                        ? (Class<T>) values.get(0).getClass()
                        : (Class<T>) org.abi.datatypes.AbiTypes.getType(values.get(0).getTypeAsString()),
                values);
    }

    @Deprecated
    @SuppressWarnings("unchecked")
    private DynamicArray(String type) {
        super((Class<T>) org.abi.datatypes.AbiTypes.getType(type));
    }

    @Deprecated
    public static DynamicArray empty(String type) {
        return new DynamicArray(type);
    }

    public DynamicArray(Class<T> type, List<T> values) {
        super(type, values);
    }

    @Override
    public int bytes32PaddedLength() {
        return super.bytes32PaddedLength() + MAX_BYTE_LENGTH;
    }

    @SafeVarargs
    public DynamicArray(Class<T> type, T... values) {
        super(type, values);
    }

    @Override
    public String getTypeAsString() {
        String type;
        // Handle dynamic array of zero length. This will fail if the dynamic array
        // is an array of structs.
        if (value.isEmpty()) {
            if (org.abi.datatypes.StructType.class.isAssignableFrom(getComponentType())) {
                type = Utils.getStructType(getComponentType());
            } else {
                type = org.abi.datatypes.AbiTypes.getTypeAString(getComponentType());
            }
        } else {
            if (StructType.class.isAssignableFrom(value.get(0).getClass())) {
                type = value.get(0).getTypeAsString();
            } else {
                type = AbiTypes.getTypeAString(getComponentType());
            }
        }
        return type + "[]";
    }
}
