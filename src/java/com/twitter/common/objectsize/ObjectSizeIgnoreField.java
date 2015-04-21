// =================================================================================================
// Copyright 2015 trivago GmbH
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.objectsize;

import java.lang.annotation.*;

/**
 * A flag annotation to tag fields which should not get counted in the ObjectSizeCalculator. This is useful
 * for references to other objects not of interest. For example, references to objects that are from a in-heap Cache
 * may be not of interest, to avoid duplicated counting when one is doing an extra measurement for that Cache.
 * <p>
 * Using this annotation REQUIRES it to be available in the runtime classpath, as it uses RetentionPolicy.RUNTIME.
 * When usage of this annotation is not feasible, an own annotation can be used. It must be set with
 * ObjectSizeCalculator#setIgnoreFieldAnnotation(Class<? extends Annotation> annotation). 
 * 
 * <p>
 * References are counted with the size of a reference in the memory model. Primitive types cannot be ignored,
 * they are always counted.
 *
 * @author Christian Esken, trivago GmbH
 *
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ObjectSizeIgnoreField
{
}

