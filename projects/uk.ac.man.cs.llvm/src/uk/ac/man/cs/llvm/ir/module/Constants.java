/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package uk.ac.man.cs.llvm.ir.module;

import java.util.List;

import uk.ac.man.cs.llvm.bc.ParserListener;
import uk.ac.man.cs.llvm.bc.records.Records;
import uk.ac.man.cs.llvm.ir.ConstantGenerator;
import uk.ac.man.cs.llvm.ir.module.records.ConstantsRecord;
import uk.ac.man.cs.llvm.ir.types.IntegerConstantType;
import uk.ac.man.cs.llvm.ir.types.IntegerType;
import uk.ac.man.cs.llvm.ir.types.Type;

public class Constants implements ParserListener {

    protected final Types types;

    protected final List<Type> symbols;

    protected final ConstantGenerator generator;

    protected Type type;

    public Constants(Types types, List<Type> symbols, ConstantGenerator generator) {
        this.types = types;
        this.symbols = symbols;
        this.generator = generator;
    }

    @Override
    public void record(long id, long[] args) {
        ConstantsRecord record = ConstantsRecord.decode(id);

        switch (record) {
            case SETTYPE:
                type = types.get(args[0]);
                return;

            case NULL:
                generator.createNull(type);
                if (type instanceof IntegerType) {
                    symbols.add(new IntegerConstantType((IntegerType) type, 0));
                    return;
                }
                break;

            case UNDEF:
                generator.createUndefined(type);
                break;

            case INTEGER: {
                long value = Records.toSignedValue(args[0]);
                generator.createInteger(type, value);
                symbols.add(new IntegerConstantType((IntegerType) type, value));
                return;
            }
            case FLOAT:
                generator.createFloatingPoint(type, args[0]);
                break;

            case AGGREGATE: {
                generator.createFromValues(type, Records.toIntegers(args));
                break;
            }
            case STRING:
                generator.creatFromString(type, Records.toString(args), false);
                break;

            case CSTRING:
                generator.creatFromString(type, Records.toString(args), true);
                break;

            case CE_BINOP:
                generator.createBinaryOperationExpression(type, (int) args[0], (int) args[1], (int) args[2]);
                break;

            case CE_CAST:
                generator.createCastExpression(type, (int) args[0], (int) args[2]);
                break;

            case CE_CMP: {
                int i = 1;
                int lhs = (int) args[i++];
                int rhs = (int) args[i++];
                int opcode = (int) args[i++];

                generator.createCompareExpression(type, opcode, lhs, rhs);
                break;
            }
            case CE_GEP:
                createGetElementPointerExpression(args, false);
                break;

            case CE_INBOUNDS_GEP:
                createGetElementPointerExpression(args, true);
                break;

            case BLOCKADDRESS:
                generator.createBlockAddress(type, (int) args[1], (int) args[2]);
                break;

            case DATA:
                generator.createFromData(type, args);
                break;

            default:
                break;
        }
        symbols.add(type);
    }

    protected void createGetElementPointerExpression(long[] args, boolean isInbounds) {
        int[] indices = new int[((args.length - 1) >> 1) - 1];

        for (int i = 0; i < indices.length; i++) {
            indices[i] = (int) args[(i + 2) << 1];
        }

        generator.createGetElementPointerExpression(type, (int) args[2], indices, isInbounds);
    }
}
