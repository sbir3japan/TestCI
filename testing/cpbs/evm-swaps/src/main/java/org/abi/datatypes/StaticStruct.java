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

import org.abi.datatypes.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StaticStruct extends StaticArray<org.abi.datatypes.Type> implements StructType {

    private final List<Class<org.abi.datatypes.Type>> itemTypes = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public StaticStruct(List<org.abi.datatypes.Type> values) {
        super(org.abi.datatypes.Type.class, values.size(), values);
        for (org.abi.datatypes.Type value : values) {
            itemTypes.add((Class<org.abi.datatypes.Type>) value.getClass());
        }
    }

    @SafeVarargs
    public StaticStruct(org.abi.datatypes.Type... values) {
        this(Arrays.asList(values));
    }

    @Override
    public String getTypeAsString() {
        final StringBuilder type = new StringBuilder("(");
        for (int i = 0; i < itemTypes.size(); ++i) {
            final Class<Type> cls = itemTypes.get(i);
            if (StructType.class.isAssignableFrom(cls) || Array.class.isAssignableFrom(cls)) {
                type.append(getValue().get(i).getTypeAsString());
            } else {
                type.append(AbiTypes.getTypeAString(cls));
            }
            if (i < itemTypes.size() - 1) {
                type.append(",");
            }
        }
        type.append(")");
        return type.toString();
    }
}
