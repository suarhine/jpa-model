package org.jpa.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.*;

import org.reflect.invoke.util.*;

/**
 * Class <code>Model</code>
 * สำหรับใช้ในการเข้าถึงหรือปฏิบัติต่อข้อมูลในตารางใดๆของระบบจัดการฐานข้อมูล ตาม
 * {@link Entity} Class ผ่าน {@link EntityManager} ของ {@link Persistence}
 * Framework
 *
 * <pre>
 * ตัวอย่างการใช้งาน
 * 1. สร้าง {@link Model} Object
 * 	{@link EntityManagerFactory} factory = {@link Persistence#createEntityManagerFactory(String)};
 * 	{@link Model}{@code<}SomeEntity{@code>} model = new {@link Model#Model(EntityManagerFactory, Class) Model<>(factory, SomeEntity.class)};
 * // หรือ
 * 	{@link Model.Factory} factory = new {@link Model.Factory.Static#Static(EntityManagerFactory, Class...) Model.Factory.Static}(
 * 			{@link Persistence#createEntityManagerFactory(String)});
 * 	{@link Model}{@code<}SomeEntity{@code>} model = {@link Factory#create(Class) factory.create(SomeEntity.class)};
 * 2. เพิ่มข้อมูล
 * 	SomeEntity entity = new SomeEntity();
 * 	entity.setSomeValue(...);
 * 	{@link #add(Object...) model.add(entity)}
 * 3. ค้นหาข้อมูล
 * 	SomeEntity find = {@link #find(Object) model.find(...)};
 * // หรือ
 * 	List{@code<}SomeEntity{@code>} finds = {@link #finds(Criteria, Object...) model.finds(...)};
 * 4. แก้ไขข้อมูล
 * 	find.setSomeValue(...);
 * 	{@link #edit(Object...) model.edit(find)};
 *  // หรือ
 * 	for(SomeEntity find : finds){
 * 		find.setSomeValue(...);
 * 	}
 * 	{@link #edit(Iterable) model.edit(finds)};
 * 5. ลบข้อมูล
 * 	{@link #del(Object...) model.del(find)};
 *  // หรือ
 * 	{@link #del(Iterable) model.del(finds)};
 * </pre>
 *
 * @since JDK 1.8, jpa-model 2.0
 * @version 2.0.0
 * @author เสือไฮ่
 * @param <E>
 *            {@link Entity} Class ของ ข้อมูลที่ {@link Model} เป็นตัวจัดการ
 * @see Model.Factory
 * @see Persistence
 */
public class Model<E> {
	/**
	 * {@code @}Interface Model.Alias ใช้สำหรับกำหนด Default Alias Name ให้กับ
	 * {@link Entity} Class ที่จะนำมาใช้ในการเขียนคำสั่ง JPQL Statement
	 *
	 * @author เสือไฮ่
	 */
	public @interface Alias {
		/**
		 * @return <b>Default Alias Name</b>
		 */
		public String value();
	}

	/**
	 * Class <code>Model.Factory</code> สำหรับใช้ในการสร้าง {@link Model} Object
	 * และเป็น Core ในการเชื่อมต่อฐานข้อมูลของ {@link Model} ที่สร้างขึ้น
	 * <p>
	 * วิธีการใช้งาน<br />
	 * 1. ประการ Class Factory
	 *
	 * <pre>
	 * public class MyModelFactory extends Model.Factory {
	 * 	{@code @}Override
	 * 	public {@code <}R{@code >} R factory(Function{@code <}EntityManagerFactory, R{@code >} function) {
	 * 		return function.apply(/{@code *} {@code T}ODO {@code *}/);
	 * 	}
	 * }
	 * </pre>
	 *
	 * 2. สร้าง Factory และ Model Object
	 *
	 * <pre>
	 * Model.Factory factory = new MyModelFactory();
	 * Model{@code <}MyEntity{@code >} model = factory.create(MyEntity.class);
	 * </pre>
	 * </p>
	 *
	 * @since JDK 1.8, reflect-invoke 2.0, jpa-model 2.0
	 * @version 1.0.0
	 * @author เสือไฮ่
	 * @see Model
	 * @see Persistence
	 */
	public static abstract class Factory {
		/**
		 * Functional Interface <code>Model.Factory.Statement</code>
		 * สำหรับสร้างคำสั่ง JPQL
		 *
		 * @author เสือไฮ่
		 */
		@FunctionalInterface
		public interface Statement {
			/**
			 * Interface <code>Model.Factory.Statement.Junction</code>
			 * สำหรับแยกวิธีในการสร้างคำสั่ง JPQL ระหว่างแบบตั้งชื่อ Parameter
			 * และ แบบลำดับ Parameter
			 */
			public interface Junction extends Statement {
				@Override
				public default void build(Model<?> model,
						StringBuilder statement,
						Map<String, Object> named,
						List<Object> index)
						throws NullPointerException, IllegalArgumentException {
					if (named == null) {
						build(model, statement, index);
					} else {
						build(model, statement, named);
					}
				}

				@Override
				public void build(Model<?> model,
						StringBuilder statement,
						List<Object> params)
						throws NullPointerException, IllegalArgumentException;

				@Override
				public void build(Model<?> model,
						StringBuilder statement,
						Map<String, Object> params)
						throws NullPointerException, IllegalArgumentException;
			}

			/**
			 * Functional Interface สำหรับใช้ในการสร้างคำสั่ง JPQL
			 *
			 * @param model
			 *            {@link Model} ที่ต้องสร้าง {@link Criteria}
			 * @param statement
			 *            {@link StringBuilder} สำหรับใช้ในการสร้างคำสั่ง JPQL
			 * @param named
			 *            Parameter ใน <code>statement</code> แบบตั้งชื่อ
			 * @param index
			 *            Parameter ใน <code>statement</code> แบบลำดับ
			 * @throws NullPointerException
			 *             <code>model</code>, <code>statement</code> หรือ
			 *             <code>named</code> และ <code>index</code> เป็น null
			 * @throws IllegalArgumentException
			 *             <code>model</code>, <code>statement</code>,
			 *             <code>named</code> หรือ <code>index</code> ไม่ถูกต้อง
			 */
			public void build(Model<?> model,
					StringBuilder statement,
					Map<String, Object> named,
					List<Object> index)
					throws NullPointerException,
					IllegalArgumentException;

			/**
			 * สร้างคำสั่ง JPQL โดยการตั้งชื่อ Parameter
			 *
			 * @param model
			 *            {@link Model} ที่ต้องสร้าง {@link Criteria}
			 * @param statement
			 *            {@link StringBuilder} สำหรับใช้ในการสร้างคำสั่ง JPQL
			 * @param params
			 *            Parameter ใน <code>statement</code>
			 * @throws NullPointerException
			 *             <code>model</code>, <code>statement</code> หรือ
			 *             <code>params</code> เป็น null
			 * @throws IllegalArgumentException
			 *             <code>model</code>, <code>statement</code> หรือ
			 *             <code>params</code> ไม่ถูกต้อง
			 */
			public default void build(Model<?> model,
					StringBuilder statement,
					Map<String, Object> params)
					throws NullPointerException,
					IllegalArgumentException {
				build(model, statement, params, null);
			}

			/**
			 * สร้างคำสั่ง JPQL โดยการลำดับ Parameter
			 *
			 * @param model
			 *            {@link Model} ที่ต้องสร้าง {@link Criteria}
			 * @param statement
			 *            {@link StringBuilder} สำหรับใช้ในการสร้างคำสั่ง JPQL
			 * @param params
			 *            Parameter ใน <code>statement</code>
			 * @throws NullPointerException
			 *             <code>model</code>, <code>statement</code> หรือ
			 *             <code>params</code> เป็น null
			 * @throws IllegalArgumentException
			 *             <code>model</code>, <code>statement</code> หรือ
			 *             <code>params</code> ไม่ถูกต้อง
			 */
			public default void build(Model<?> model,
					StringBuilder statement,
					List<Object> params)
					throws NullPointerException,
					IllegalArgumentException {
				build(model, statement, null, params);
			}
		}

		/**
		 * Interface <code>Model.Factory.Selector</code>
		 * สำหรับใช้ในการระบุคำสั่งในการเลือกค่าจากผลลัพธ์ของการค้นหา
		 * และแปลงค่าที่ได้ให้อยู่ในรูปแบบที่ต้องการ
		 *
		 * @author เสือไฮ่
		 * @param <S>
		 *            Class ที่เลือกออกมาจาก Query Statement
		 * @param <R>
		 *            Class ที่ต้องการ Return กลับ
		 */
		public interface Selector<S, R> {

			/**
			 * Functional Interface <code>Model.Factory.Selector.That</code>
			 * สำหรับใช้เรียกค่าจากผลลัพธ์จากการค้นหาตามคำสั่งเลย
			 *
			 * @author เสือไฮ่
			 * @param <R>
			 *            Class ที่ต้องการ Return กลับ
			 */
			@FunctionalInterface
			public interface That<R> extends Selector<R, R> {
				@Override
				public default R result(R result) {
					return result;
				}
			}

			/**
			 * เรียก Class ของผลลัพธ์ของคำสั่ง
			 *
			 * @return Class ของผลลัพธ์ของคำสั่ง
			 */
			public default Class<S> clazz() {
				return Generic.$(this, Selector.class, "S");
			}

			/**
			 * เรียกคำสั่งสำหรับระบุผลลัพธ์ของการค้นหา
			 *
			 * @param model
			 *            {@link Model} ของคำสั่งสำหรับระบุผลลัพธ์ของการค้นหา
			 * @return คำสั่งสำหรับระบุผลลัพธ์ของการค้นหา
			 */
			public CharSequence selector(Model<?> model);

			/**
			 * เรียกผลลัพธ์จากการค้นหาในรูปแบบที่ต้องการ
			 *
			 * @param result
			 *            ผลลัพธ์จากการค้นหน
			 * @return ผลลัพธ์จากการค้นหาในรูปแบบที่ต้องการ
			 */
			public R result(S result);
		}

		/**
		 * Functional Interface <code>Model.Factory.Criteria</code>
		 * สำหรับใช้ในการสร้างคำสั่ง JPQL
		 * ในการกำหนดเกณฑ์หรือเงื่อนไขในการระบุข้อมูลในฐานข้อมูล
		 *
		 * @since JDK 1.8, jpa-model 2.0
		 * @version 1.0.0
		 * @author เสือไฮ่
		 */
		@FunctionalInterface
		public interface Criteria extends Statement {
			/**
			 * Interface <code>Model.Factory.Criteria.Junction</code>
			 * สำหรับแยกวิธีในการสร้างคำสั่ง JPQL ระหว่างแบบตั้งชื่อ Parameter
			 * และ แบบลำดับ Parameter
			 */
			public interface Junction extends Criteria, Statement.Junction {}

			/**
			 * บ่งบอกว่า {@link Criteria} มีการสร้างคำสั่ง JPQL โดยการตั้งชื่อ
			 * Parameter หรือไม่
			 *
			 * @return true : ตั้งชื่อ Parameter, false : ลำดับ Parameter
			 */
			public default boolean isNaming() {
				return false;
			}
		}

		/**
		 * Functional Interface <code>Model.Factory.Injector</code>
		 * สำหรับใช้ในการผูกค่าหรือกำหนดบางสิ่งบางอย่างให้กับ {@link Query}
		 * ก่อนที่จะไปปฏิบัติต่อฐานข้อมูล ({@link Query#executeUpdate()} หรือ
		 * {@link Query#getResultList()})
		 *
		 * @since JDK 1.8, jpa-model 2.0
		 * @version 1.0.0
		 * @author เสือไฮ่
		 */
		@FunctionalInterface
		public interface Injector {
			/**
			 * Functional Interface สำหรับผูกค่าหรือกำหนดบางสิ่งบางอย่างให้กับ
			 * {@link Query}
			 *
			 * @param query
			 *            {@link Query} ที่จะปฏิบัติต่อฐานข้อมูล
			 */
			public void inject(Query query);
		}

		/**
		 * Class <code>Model.Factory.Pair</code> เป็น Class สำหรับเก็บคู่อันดับ
		 * (Field - Value) ของข้อมูล
		 *
		 * @since JDK 1.8, jpa-model 2.0
		 * @version 1.0.0
		 * @author เสือไฮ่
		 */
		protected static class Pair {
			/**
			 * Field ของคู่อันดับ (Field - Value)
			 */
			protected final String field;
			/**
			 * Value ของคู่อันดับ (Field - Value)
			 */
			protected final Object value;

			/**
			 * Constructor สำหรับสร้าง {@link Pair} Object
			 *
			 * @param field
			 *            {@link #field}
			 * @param value
			 *            {@link #value}
			 * @throws NullPointerException
			 *             <code>field</code> เป็น null หรือ ""
			 */
			public Pair(String field, Object value)
					throws NullPointerException {
				if ((this.field = field.trim()).isEmpty())
					throw new NullPointerException();
				this.value = value;
			}
		}

		/**
		 * Class <code>Model.Factory.Logic</code> เป็น Class
		 * สำหรับสร้างเกณฑ์หรือเงื่อนไขในการระบุข้อมูลในฐานข้อมูล
		 * โดยการเปรียบเทียบว่าค่าในฐานข้อมูล ณ Field ที่สนใจ
		 * มีค่าสอดคล้องตามเงื่อนไขที่ระบุกับ Value หรือไม่
		 *
		 * @since JDK 1.8, jpa-model 2.0
		 * @version 1.0.0
		 * @author เสือไฮ่
		 */
		protected static class Logic extends Pair implements Criteria.Junction {
			/**
			 * วิธีในการเปรียบเทียบค่า ณ คู่อันดับ (Field - Value)
			 */
			protected final String condition;
			/**
			 * ชื่อ Parameter ที่จะกำหนดในการสร้าง {@link Criteria}
			 */
			protected final String name;

			/**
			 * Constructor สำหรับสร้าง {@link Logic} Object
			 *
			 * @param field
			 *            {@link Pair#field field}
			 * @param condition
			 *            {@link #condition}
			 * @param value
			 *            {@link Pair#value value}
			 * @param name
			 *            {@link #name}
			 * @throws NullPointerException
			 *             <code>field</code> เป็น null หรือ ""
			 * @see Model.Factory.Pair#Pair(String, Object)
			 */
			public Logic(
					String field, String condition, Object value, String name)
					throws NullPointerException {
				super(field, value);
				if (value == Void.class || value == void.class) {
					this.condition = this.name = null;
				} else {
					this.condition = condition == null
							|| (condition = condition.trim()).isEmpty()
									? "=" : condition;
					this.name = name == null || (name = name.trim()).isEmpty()
							? null : name;
				}
			}

			/**
			 * เพิ่ม {@link Pair#value value} ลงใน Parameter (แบบ Naming)
			 * 
			 * @param params
			 *            Parameter ที่ต้องการเพิ่ม {@link Pair#value value}
			 *            ลงไป
			 * @param name
			 *            ชื่อ Parameter ของ {@link Pair#value value}
			 * @throws IllegalArgumentException
			 *             ชื่อ Parameter ซ้ำกับชื่อ Parameter ตัวอื่น
			 */
			private void iput(Map<String, Object> params, String name)
					throws IllegalArgumentException {
				if (value == null) {
					if (!params.containsKey(name)) {
						params.put(name, null);
					}
				} else {
					Object release = params.put(name, value);
					if (release != null && !release.equals(value)) {
						throw new IllegalArgumentException(
								"Parameter \"" + name + "\" was conflict");
					}
				}
			}

			@Override
			public boolean isNaming() {
				return this.name != null;
			}

			@Override
			public void build(Model<?> model,
					StringBuilder statement,
					Map<String, Object> params)
					throws IllegalArgumentException {
				Pattern pattern = Pattern.compile(":\\w+");
				Matcher matcher;
				if ((matcher = pattern.matcher(field)).find()) {
					statement.append(model.ialias(field));
					iput(params, matcher.group().substring(1));
				} else if ((matcher = pattern.matcher(condition)).find()) {
					statement.append(model.ialias(field)).append(' ')
							.append(condition);
					iput(params, matcher.group().substring(1));
				}
				statement.append(model.ialias(field));
				if (value == null) {
					if (condition.equals("=")) {
						statement.append(" IS NULL");
					} else if (condition.equals("!=")) {
						statement.append(" IS NOT NULL");
					} else {
						statement.append(' ').append(condition).append(" NULL");
					}
				} else if (value instanceof Factory.Statement) {
					statement.append(' ').append(condition).append(" (");
					((Factory.Statement) value).build(model, statement, params);
					statement.append(')');
				} else {
					iput(params, this.name == null
							? field.replace('.', '_') : this.name);
					statement.append(' ').append(condition)
							.append(" :").append(name);
				}
			}

			@Override
			public void build(Model<?> model,
					StringBuilder statement,
					List<Object> params) {
				if (field.indexOf('?') >= 0) {
					params.add(value);
					statement.append(model.ialias(field).toString()
							.replace("?", "?" + params.size()));
				} else if (value == Void.class || value == void.class) {
					statement.append(model.ialias(field));
				} else if (condition.indexOf('?') >= 0) {
					params.add(value);
					statement.append(model.ialias(field)).append(' ').append(
							condition.replace("?", "?" + params.size()));
				} else {
					statement.append(model.ialias(field));
					if (value == null) {
						if (condition.equals("=")) {
							statement.append(" IS NULL");
						} else if (condition.equals("!=")) {
							statement.append(" IS NOT NULL");
						} else {
							statement.append(' ')
									.append(condition).append(" NULL");
						}
					} else if (value instanceof Factory.Statement) {
						statement.append(' ').append(condition).append(" (");
						((Factory.Statement) value)
								.build(model, statement, params);
						statement.append(')');
					} else {
						params.add(value);
						statement.append(' ').append(condition)
								.append(" ?").append(params.size());
					}
				}
			}
		}

		/**
		 * Class <code>Model.Factory.Chain</code> สำหรับเชื่อม {@link Criteria}
		 * หลายๆตัวเข้าด้วยกัน
		 *
		 * @since JDK 1.8, jpa-model 2.0
		 * @version 1.0.0
		 * @author เสือไฮ่
		 */
		protected static class Chain implements Criteria.Junction {
			/**
			 * {@link Criteria} ทั้งหมดที่จะนำมาเชื่อมเข้าด้วยกัน
			 */
			private final ArrayList<Criteria> criteria = new ArrayList<>();

			/**
			 * วิธีเชื่อม {@link #criteria} แต่ละตัว
			 */
			private final ArrayList<CharSequence> conjunct = new ArrayList<>();
			/**
			 * ระบุความเป็นนิเสธของ {@link Criteria} ทั้งคำสั่ง
			 */
			protected boolean negation;

			/**
			 * เชื่อม {@link Criteria} เข้าด้วยกันโดยใช้ตัวเชื่อมประโยค
			 *
			 * @param conjunct
			 *            ตัวเชื่อมประโยคสำหรับเชื่อม <code>criteria</code>
			 *            ที่ต้องการ เข้ากับ {@link Criteria}ก่อนหน้า
			 * @param criteria
			 *            {@link Criteria} ย่อย ที่จะนำมาเชื่อมกันเป็น
			 *            {@link Criteria} เดียวกัน
			 * @throws NullPointerException
			 *             <code>conjunct</code> หรือ <code>criteria</code> เป็น
			 *             null หรือ ""
			 */
			protected void append(CharSequence conjunct, Criteria criteria)
					throws NullPointerException {
				if (criteria == null)
					throw new NullPointerException();
				else if (this.criteria.isEmpty()) {
					this.criteria.add(criteria);
				} else if (conjunct.length() == 0)
					throw new NullPointerException();
				else {
					this.conjunct.add(conjunct);
					this.criteria.add(criteria);
				}
			}

			/**
			 * สร้าง {@link Criteria} จาก {@link #criteria} ทั้งหมด
			 *
			 * @param statement
			 *            {@link StringBuilder} สำหรับใช้ในการสร้างคำสั่ง JPQL
			 * @param consumer
			 *            ตัวรับ {@link Criteria} จาก {@link #criteria} ไปสร้าง
			 *            <code>statement</code> ต่อ
			 * @throws NullPointerException
			 *             <code>statement</code> หรือ <code>consumer</code>
			 *             เป็น null
			 * @throws IllegalArgumentException
			 *             การสร้าง <code>statement</code> ใน
			 *             <code>consumer</code> เกิดข้อผิดพลาด
			 */
			protected void build(
					StringBuilder statement, Consumer<Criteria> consumer)
					throws NullPointerException, IllegalArgumentException {
				if (criteria.isEmpty()) {
					if (negation) {
						statement.append("NOT");
					}
					return;
				}
				if (negation) {
					statement.append("NOT (");
				} else if (criteria.size() > 1) {
					statement.append('(');
				}
				Iterator<Criteria> i = criteria.iterator();
				consumer.accept(i.next());
				for (CharSequence conjunct : conjunct) {
					statement.append(' ').append(conjunct).append(' ');
					consumer.accept(i.next());
				}
				if (negation || criteria.size() > 1) {
					statement.append(')');
				}
			}

			@Override
			public boolean isNaming() {
				for (Criteria criteria : criteria) {
					if (criteria.isNaming()) return true;
				}
				return false;
			}

			@Override
			public void build(Model<?> model,
					StringBuilder statement,
					Map<String, Object> params)
					throws NullPointerException,
					IllegalArgumentException {
				build(statement,
						criteria -> criteria.build(model, statement, params));
			}

			@Override
			public void build(Model<?> model,
					StringBuilder statement,
					List<Object> params)
					throws NullPointerException,
					IllegalArgumentException {
				build(statement,
						criteria -> criteria.build(model, statement, params));
			}
		}

		/**
		 * Class <code>Model.Factory.Static</code> สำหรับสร้าง {@link Factory}
		 * Object ได้ทันที โดยไม่ต้องประกาศ Class ขึ้นมาเอง เพียงแต่ตอนสร้าง
		 * Object ต้องระบุให้ได้ว่าจะใช้ {@link EntityManagerFactory}
		 * ใดเป็นตัวสร้าง {@link EntityManager} ซึ่งเป็น Core
		 * หลักในการเชื่อมต่อกับฐานข้อมูล
		 *
		 * @since JDK 1.8, jpa-model 2.0
		 * @version 1.0.0
		 * @author เสือไฮ่
		 */
		public static class Static extends Factory {

			/**
			 * ตัวสร้าง {@link EntityManager} ซึ่งเป็น Core
			 * หลักในการเชื่อมต่อกับฐานข้อมูล
			 */
			private final EntityManagerFactory factory;

			/**
			 * Constructor สำหรับสร้าง {@link Factory.Static} Object
			 *
			 * @param factory
			 *            {@link #factory}
			 * @param register
			 *            {@link #register}
			 * @throws NullPointerException
			 *             <code>factory</code> เป็น null
			 * @see Model.Factory#Factory(Class...)
			 */
			@SafeVarargs
			public Static(EntityManagerFactory factory,
					Class<? extends Model<?>>... register)
					throws NullPointerException {
				super(register);
				if ((this.factory = factory) == null)
					throw new NullPointerException();
			}

			@Override
			public <R> R factory(Function<EntityManagerFactory, R> function) {
				return function.apply(factory);
			}
		}

		/**
		 * Class <code>Model.Factory.Unit</code> เป็น Class สำหรับสร้าง
		 * {@link Factory} Object ได้ทันที โดยไม่ต้องประกาศ Class ขึ้นมาเอง
		 * เพียงแต่ตอนสร้าง PersistanceUnit Object ต้องระบุให้ได้ว่าจะใช้
		 * Persistence Unit Name ใดในการสร้าง {@link EntityManagerFactory}
		 * Object
		 *
		 * @since JDK 1.8, jpa-model 2.0
		 * @version 1.0.0
		 * @author เสือไฮ่
		 * @see Persistence#createEntityManagerFactory(String)
		 */
		public static class UnitName extends Factory {

			/**
			 * Persistence Unit Name สำหรับสร้าง {@link EntityManagerFactory}
			 */
			private final String name;

			/**
			 * Constructor สำหรับสร้าง {@link Factory.UnitName} Object
			 *
			 * @param name
			 *            {@link #name}
			 * @param register
			 *            {@link #register}
			 * @see Model.Factory#Factory(Class...)
			 */
			@SafeVarargs
			public UnitName(String name,
					Class<? extends Model<?>>... register) {
				super(register);
				this.name = name;
			}

			@Override
			public <R> R factory(Function<EntityManagerFactory, R> function)
					throws UnsupportedOperationException {
				EntityManagerFactory factory;
				try {
					factory = Persistence.createEntityManagerFactory(name);
				} catch (Throwable e) {
					throw new UnsupportedOperationException(e);
				}
				return function.apply(factory);
			}
		}

		/**
		 * Class ของ {@link Model} ที่จะใช้ในการสร้าง {@link Model} Object
		 *
		 * @see #create(Class, String)
		 */
		private final Class<? extends Model<?>>[] register;

		/**
		 * Constructor สำหรับสร้าง {@link Factory} Object
		 *
		 * @param register
		 *            {@link #register}
		 */
		@SafeVarargs
		public Factory(Class<? extends Model<?>>... register) {
			this.register = register;
		}

		/**
		 * สร้าง Instance ของ {@link Model} จาก {@link Class} ที่ระบุ
		 *
		 * @param clazz
		 *            {@link Class} ที่ต้องการสร้าง Instance
		 * @param alias
		 *            ชื่อ Alias Name ใน Model
		 * @return Instance ของ {@link Model}
		 * @throws NullPointerException
		 *             <code>clazz</code> เป็น null
		 * @throws InvocationTargetException
		 *             เกิด {@link Throwable} ขึ้นในการสร้าง Instance
		 * @throws NoSuchMethodException
		 *             ไม่มี {@link Constructor} สำหรับสร้าง Instance
		 */
		protected Model<?> found(Class<? extends Model<?>> clazz, String alias)
				throws NullPointerException,
				InvocationTargetException,
				NoSuchMethodException {
			return new Invocable<>(clazz).found(c -> true, (i, p) -> {
				Class<?> type = p.getType();
				if (type == Class.class)
					return clazz;
				else if (type == Factory.class)
					return Factory.this;
				else if (type == EntityManagerFactory.class)
					return factory(f -> f);
				else if (type == String.class)
					return alias;
				else return null;
			});
		}

		/**
		 * เรียก {@link Field} Primary Key จาก {@link Entity} Class
		 * ที่ต้องการได้
		 *
		 * @param clazz
		 *            {@link Entity} Class ที่ต้องการ Primary Key
		 * @return {@link Field} Primary Key ของ {@link Entity} Class ที่ต้องการ
		 * @throws NullPointerException
		 *             <code>clazz</code> เป็น null
		 * @throws IllegalArgumentException
		 *             ไม่มี Primary Key ใน <code>clazz</code> ที่ระบุ
		 * @see Id
		 * @see EmbeddedId
		 * @see Class#getDeclaredFields()
		 */
		protected <E> Field pk(Class<E> clazz)
				throws NullPointerException, IllegalArgumentException {
			for (Class<?> c = clazz; c != Object.class; c = c.getSuperclass()) {
				for (Field field : c.getDeclaredFields()) {
					if (field.getAnnotation(Id.class) != null
							|| field.getAnnotation(EmbeddedId.class) != null)
						return field;
				}
			}
			String msg = clazz.getName()
					+ " is not @Id (or @EmbeddedId) present.";
			throw new IllegalArgumentException(msg);
		}

		/**
		 * เรียกค่า ID จาก {@link Entity} Object
		 *
		 * @param clazz
		 *            {@link Entity} Class ที่ต้องการเรียกค่า ID
		 * @param entity
		 *            {@link Entity} Object ที่ต้องการเรียกค่า ID
		 * @return ID ของ {@link Entity} Object
		 * @throws NullPointerException
		 *             <code>clazz</code> เป็น null
		 * @throws IllegalArgumentException
		 *             <code>entity</code> ไม่ใช่ Object ของ <code>clazz</code>
		 *             หรือ ไม่ใช่ Object ของ PK ของ <code>clazz</code>
		 */
		protected <E> Object id(Class<E> clazz, Object entity)
				throws NullPointerException, IllegalArgumentException {
			Field pk = pk(clazz);
			if (clazz.isInstance(entity)) {
				try {
					return Invocable.override(pk).get(entity);
				} catch (Throwable e) {
					throw new IllegalArgumentException(e);
				}
			} else if (pk.getType().isInstance(entity)) return entity;
			try (Formatter f = new Formatter()) {
				f.format("%s is not instance of %s or %s.%s (%s).",
						entity, clazz.getName(), clazz.getSimpleName(),
						pk.getName(), pk.getType().getName());
				throw new IllegalArgumentException(f.toString());
			}
		}

		/**
		 * ตรวจสอบว่าใน Keyword สำหรับการอ้าง Field ใน Entity Class มีตัวแปร
		 * Alias Name อยู่แล้วหรือไม่
		 *
		 * @param model
		 *            {@link Model} ของ Entity Class
		 * @param keyword
		 *            Keyword ที่ต้องการตรวจสอบ
		 * @return true หากใน <code>keyword</code> มี Alias Name อยู่แล้ว
		 */
		protected boolean hasAlias(Model<?> model, String keyword) {
			try {
				StringBuilder builder = new StringBuilder()
						.append("^ *").append(model.as).append(" *$")
						.append("|^ *").append(model.as).append("\\.")
						.append("|\\W").append(model.as).append("\\.")
						.append("|\\( *").append(model.as).append(" *\\)");
				return Pattern.compile(builder.toString())
						.matcher(keyword).find();
			} catch (Throwable e) {
				return false;
			}
		}

		/**
		 * สร้างคำสั่ง JPQL และ Parameter ที่จะนำไปสร้างเป็น {@link Query}
		 *
		 * @param model
		 *            {@link Model} ที่ต้องสร้างคำสั่ง JPQL
		 * @param statement
		 *            {@link StringBuilder} สำหรับใช้ในการสร้างคำสั่ง JPQL
		 * @param params
		 *            Parameter
		 * @return Parameter ใน <code>statement</code>
		 * @throws NullPointerException
		 *             <code>model</code> หรือ <code>statement</code> เป็น null
		 * @throws IllegalArgumentException
		 *             ไม่สามารถสร้างคำสั่งตาม <code>model</code>,
		 *             <code>statement</code> และ <code>params</code>
		 *             ที่กำหนดได้
		 * @see Model.Factory.Statement#build(Model, StringBuilder, List)
		 */
		protected Object[] build(
				Model<?> model, StringBuilder statement, Object... params)
				throws NullPointerException, IllegalArgumentException {
			if (params == null || params.length == 0) return params;
			else if (params[0] instanceof Map) {
				ArrayList<Object> list = new ArrayList<>();
				Map<String, Object> map = Cast.$(params[0]);
				for (Object param : params) {
					if (param instanceof Statement) {
						((Statement) param).build(model, statement, map);
					} else {
						list.add(param);
					}
				}
				return list.toArray();
			} else {
				ArrayList<Object> list = new ArrayList<>();
				for (Object param : params) {
					if (param instanceof Statement) {
						((Statement) param).build(model, statement, list);
					} else {
						list.add(param);
					}
				}
				return list.toArray();
			}
		}

		/**
		 * ผูกค่า Parameter เข้ากับ {@link Query}
		 * ที่จะใช้ในการเข้าถึงหรือปฏิบัติต่อฐานข้อมูล
		 *
		 * @param query
		 *            {@link Query} ในการเข้าถึงหรือปฏิบัติต่อฐานข้อมูล
		 * @param params
		 *            Parameter ที่ต้องการผูกกับ {@link Query}
		 * @return <code>query</code>
		 * @throws NullPointerException
		 *             <code>query</code> เป็น null
		 * @throws IllegalArgumentException
		 *             การผูก <code>params</code> เข้ากับ <code>query</code>
		 *             ไม่ถูกต้อง
		 * @see Injector
		 * @see Query#setParameter(int, Object)
		 * @see Query#setParameter(String, Object)
		 */
		protected <Q extends Query> Q inject(Q query, Object... params)
				throws NullPointerException, IllegalArgumentException {
			if (params == null || params.length == 0) {
				for (Parameter<?> parameter : query.getParameters()) {
					if (!query.isBound(parameter))
						throw new IllegalArgumentException(
								parameter + " was not bound.");
				}
				return query;
			}
			ArrayList<Object> list = new ArrayList<>();
			for (Object param : params) {
				if (param instanceof Injector) {
					((Injector) param).inject(query);
				} else {
					list.add(param);
				}
			}
			if (list.size() > 1) {
				int i = 1;
				for (Object param : list) {
					if (param instanceof Injector) {
						((Injector) param).inject(query);
					} else {
						query.setParameter(i++, param);
					}
				}
			} else if (list.size() == 1) {
				Object param = list.get(0);
				if (param == null) {
					query.setParameter(1, null);
				} else if (param instanceof Map) {
					for (Map.Entry<?, ?> entry : ((Map<?, ?>) param)
							.entrySet()) {
						Object value = entry.getValue();
						if (value instanceof Injector) {
							((Injector) value).inject(query);
						} else {
							try {
								String key = entry.getKey().toString();
								query.setParameter(key, value);
							} catch (java.lang.NullPointerException e) {}
						}
					}
				} else if ((param instanceof Iterable
						|| param instanceof Iterator
						|| param.getClass().isArray())
						&& query.getParameters().size() > 1)
					return inject(query, Cast.$(Object[].class, param));
				else {
					query.setParameter(1, param);
				}
			}
			return inject(query);
		}

		/**
		 * เพิ่มข้อมูลลงฐานข้อมูล
		 *
		 * @param model
		 *            {@link Model} ของข้อมูล
		 * @param entities
		 *            ข้อมูลที่ต้องการเพิ่มลงฐาน
		 * @return true หากเพิ่มข้อมูลในฐานได้สำเร็จ
		 * @throws NullPointerException
		 *             <code>model</code> เป็น null
		 * @see #transaction(Function)
		 * @see EntityManager#persist(Object)
		 */
		protected <E> boolean add(Model<E> model, Iterable<E> entities)
				throws NullPointerException {
			try {
				return transaction(manager -> {
					for (E entity : entities) {
						manager.persist(entity);
					}
					return true;
				});
			} catch (Throwable e) {
				model.caught(e);
				return false;
			}
		}

		/**
		 * แก้ไขข้อมูลในฐานข้อมูล
		 *
		 * @param model
		 *            {@link Model} ของข้อมูล
		 * @param entities
		 *            ข้อมูลที่ต้องการให้แก้ไขในฐาน
		 * @return true หากแก้ไขข้อมูลในฐานได้สำเร็จ
		 * @throws NullPointerException
		 *             <code>model</code> เป็น null
		 * @see #transaction(Function)
		 * @see EntityManager#merge(Object)
		 */
		protected <E> boolean edit(Model<E> model, Iterable<E> entities)
				throws NullPointerException {
			ArrayList<E[]> merged = new ArrayList<>();
			try {
				return transaction(manager -> {
					for (E entity : entities) {
						merged.add(Cast.array(entity, manager.merge(entity)));
					}
					return true;
				});
			} catch (Throwable e) {
				model.caught(e);
				return false;
			} finally {
				for (E[] entry : merged) {
					Cast.clone(entry[0], entry[1]);
				}
			}
		}

		/**
		 * แก้ไขข้อมูลในฐานข้อมูลโดยการกำหนดค่าและระบุเงื่อนไขของข้อมูลที่ต้องการ
		 *
		 * @param model
		 *            {@link Model} ของข้อมูล
		 * @param values
		 *            ค่าที่ต้องกำหนดให้กับข้อมูลที่จะแก้ไข
		 * @param criteria
		 *            เงื่อนใขในการระบุข้อมูลที่ต้องการแก้ไข
		 * @param params
		 *            Parameter ใน <code>values</code> และ <code>criteria</code>
		 *            (รวมกันโดยที่ Parameter ใน <code>value</code> ขึ้นก่อน
		 *            และตามด้วย <code>criteria</code> ต่อเลย)
		 * @return จำนวนข้อมูลที่ถูกแก้ใขให้มีผลตาม<code>value</code>ที่ระบุ
		 *         <br />
		 *         (ไม่ว่าจะเปลี่ยนแปลค่าหรือค่าเหมือนเดิมก็ตาม,
		 *         หากไม่สามารถแก้ไขข้อมูลได้ จะ return -1)
		 * @throws NullPointerException
		 *             <code>model</code> เป็น null
		 * @see #jpql(Function, CharSequence, Object...)
		 * @see Query#executeUpdate()
		 */
		protected <E> int edit(Model<E> model,
				CharSequence values,
				CharSequence criteria,
				Object... params) throws NullPointerException {
			try {
				StringBuilder statement = new StringBuilder("UPDATE ")
						.append(model.clazz.getSimpleName()).append(model.as)
						.append(" SET ").append(values);
				if (criteria != null && criteria.length() > 0) {
					statement.append(" WHERE ").append(criteria);
				}
				return jpql(query -> query.executeUpdate(), statement,
						build(model, statement, params));
			} catch (Throwable e) {
				model.caught(e);
				return -1;
			}
		}

		/**
		 * ลบข้อมูลในฐานข้อมูล ณ ID ที่ระบุ
		 *
		 * @param model
		 *            {@link Model} ของข้อมูล
		 * @param id
		 *            ข้อมูลที่ต้องการลบ (Entity Object หรือ ID ก็ได้)
		 * @return true หากสามารถลบข้อมูลในฐานได้สำเร็จ
		 * @throws NullPointerException
		 *             <code>model</code> เป็น null
		 * @see #id(Class, Object)
		 * @see #transaction(Function)
		 * @see EntityManager#getReference(Class, Object)
		 * @see EntityManager#remove(Object)
		 */
		protected <E> boolean del(Model<E> model, Iterable<Object> id)
				throws NullPointerException {
			try {
				return transaction(manager -> {
					for (Object i : id) {
						manager.remove(manager.getReference(
								model.clazz, id(model.clazz, i)));
					}
					return true;
				});
			} catch (Throwable e) {
				model.caught(e);
				return false;
			}
		}

		/**
		 * ลบข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
		 *
		 * @param model
		 *            {@link Model} ของข้อมูล
		 * @param criteria
		 *            เงื่อนใขในการระบุข้อมูลที่ต้องการลบ
		 * @param params
		 *            Parameter ใน <code>criteria</code>
		 * @return จำนวนข้อมูลที่ถูกลบ <br />
		 *         (หากไม่สามารถลบข้อมูลได้ จะ return -1)
		 * @throws NullPointerException
		 *             <code>model</code> เป็น null
		 * @see #jpql(Function, CharSequence, Object...)
		 * @see Query#executeUpdate()
		 */
		protected <E> int del(
				Model<E> model, CharSequence criteria, Object... params)
				throws NullPointerException {
			try {
				StringBuilder statement = new StringBuilder("DELETE FROM ")
						.append(model.clazz.getSimpleName())
						.append(model.as);
				if (criteria != null && criteria.length() > 0) {
					statement.append(" WHERE ").append(criteria);
				}
				return jpql(query -> query.executeUpdate(), statement,
						build(model, statement, params));
			} catch (Throwable e) {
				model.caught(e);
				return -1;
			}
		}

		/**
		 * ค้นหาข้อมูลในฐานข้อมูล ณ ID ที่ระบุ
		 *
		 * @param model
		 *            {@link Model} ของข้อมูล
		 * @param id
		 *            ID ของข้อมูลที่ต้องการ
		 * @return ข้อมูลในฐานข้อมูล ณ ID ที่ระบุ
		 * @throws NullPointerException
		 *             <code>model</code> เป็น null
		 * @throws IllegalArgumentException
		 *             <code>id</code> ไม่ใช่ Object ของ Primary Key ของ
		 *             <code>model</code>
		 * @throws UnsupportedOperationException
		 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
		 * @see #id(Class, Object)
		 * @see #manager(Function)
		 * @see EntityManager#find(Class, Object)
		 */
		protected <E> E find(Model<E> model, Object id)
				throws NullPointerException,
				IllegalArgumentException,
				UnsupportedOperationException {
			if (id == null) return null;
			return manager(manager -> {
				try {
					return manager.find(model.clazz, id(model.clazz, id));
				} catch (Throwable e) {
					model.caught(e);
					return null;
				}
			});
		}

		/**
		 * ค้นหาข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
		 *
		 * @param model
		 *            {@link Model} ของข้อมูล
		 * @param criteria
		 *            เงื่อนไขในการระบุข้อมูลที่ต้องการค้นหา
		 * @param params
		 *            Parameter ใน <code>criteria</code>
		 * @return ข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
		 * @throws NullPointerException
		 *             <code>model</code> เป็น null
		 * @throws IllegalArgumentException
		 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
		 *             ไม่ถูกต้อง
		 * @throws UnsupportedOperationException
		 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
		 * @see #jpql(Function, Class, CharSequence, Object...)
		 * @see #build(Model, StringBuilder, Object...)
		 * @see TypedQuery#getSingleResult()
		 */
		protected <E> E find(
				Model<E> model, CharSequence criteria, Object... params)
				throws NullPointerException,
				IllegalArgumentException,
				UnsupportedOperationException {
			StringBuilder statement = new StringBuilder("SELECT ")
					.append(model.as).append(" FROM ")
					.append(model.clazz.getSimpleName())
					.append(' ').append(model.as);
			if (criteria != null && criteria.length() > 0) {
				statement.append(" WHERE ").append(criteria);
			}
			return jpql(query -> {
				try {
					return query.getSingleResult();
				} catch (NoResultException e) {
					return null;
				} catch (Throwable e) {
					model.caught(e);
					return null;
				}
			}, model.clazz, statement, build(model, statement, params));
		}

		/**
		 * ค้นหาข้อมูลในฐานข้อมูลโดยระบุข้อมูลที่ต้องการตามเงื่อนไขที่ระบุ
		 *
		 * @param model
		 *            {@link Model} ของข้อมูล
		 * @param selector
		 *            ตัวระบุข้อมูลที่ต้องการ
		 * @param criteria
		 *            เงื่อนไขในการระบุข้อมูลที่ต้องการค้นหา
		 * @param params
		 *            Parameter ใน <code>criteria</code>
		 * @return ข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
		 * @throws NullPointerException
		 *             <code>model</code> เป็น null
		 * @throws IllegalArgumentException
		 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
		 *             ไม่ถูกต้อง
		 * @throws UnsupportedOperationException
		 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
		 * @see #jpql(Function, Class, CharSequence, Object...)
		 * @see #build(Model, StringBuilder, Object...)
		 * @see TypedQuery#getResultList()
		 */
		protected <S, R> R find(Model<?> model,
				Selector<S, R> selector,
				CharSequence criteria,
				Object... params)
				throws NullPointerException,
				IllegalArgumentException,
				UnsupportedOperationException {
			StringBuilder statement = new StringBuilder("SELECT ")
					.append(selector.selector(model)).append(" FROM ")
					.append(model.clazz.getSimpleName())
					.append(' ').append(model.as);
			if (criteria != null && criteria.length() > 0) {
				statement.append(" WHERE ").append(criteria);
			}
			return jpql(query -> {
				try {
					return selector.result(query.getSingleResult());
				} catch (Throwable e) {
					model.caught(e);
					return null;
				}
			}, selector.clazz(), statement, build(model, statement, params));
		}

		/**
		 * ค้นหาข้อมูลในฐานข้อมูล ณ ID ที่ระบุ
		 *
		 * @param model
		 *            {@link Model} ของข้อมูล
		 * @param id
		 *            ID ของข้อมูลที่ต้องการ
		 * @return ข้อมูลในฐานข้อมูล ณ ID ที่ระบุ
		 * @throws NullPointerException
		 *             <code>model</code> เป็น null
		 * @throws IllegalArgumentException
		 *             <code>id</code> ไม่ใช่ Object ของ Primary Key ของ
		 *             <code>model</code>
		 * @throws UnsupportedOperationException
		 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
		 * @see #id(Class, Object)
		 * @see #finds(Model, CharSequence, Object...)
		 */
		protected <E> List<E> finds(Model<E> model, Object... id)
				throws NullPointerException,
				IllegalArgumentException,
				UnsupportedOperationException {
			if (id == null || id.length == 0)
				return finds(model, (CharSequence) null);
			id = id.clone();
			for (int i = 0; i < id.length; i++) {
				id[i] = id(model.clazz, id[i]);
			}
			return finds(model, pk(model.clazz).getName() + " IN ?", id);
		}

		/**
		 * ค้นหาข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
		 *
		 * @param model
		 *            {@link Model} ของข้อมูล
		 * @param criteria
		 *            เงื่อนไขในการระบุข้อมูลที่ต้องการค้นหา
		 * @param params
		 *            Parameter ใน <code>criteria</code>
		 * @return ข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
		 * @throws NullPointerException
		 *             <code>model</code> เป็น null
		 * @throws IllegalArgumentException
		 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
		 *             ไม่ถูกต้อง
		 * @throws UnsupportedOperationException
		 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
		 * @see #jpql(Function, Class, CharSequence, Object...)
		 * @see #build(Model, StringBuilder, Object...)
		 * @see TypedQuery#getResultList()
		 */
		protected <E> List<E> finds(
				Model<E> model, CharSequence criteria, Object... params)
				throws NullPointerException,
				IllegalArgumentException,
				UnsupportedOperationException {
			StringBuilder statement = new StringBuilder("SELECT ")
					.append(model.as).append(" FROM ")
					.append(model.clazz.getSimpleName())
					.append(' ').append(model.as);
			if (criteria != null && criteria.length() > 0) {
				statement.append(" WHERE ").append(criteria);
			}
			return jpql(query -> {
				try {
					return query.getResultList();
				} catch (Throwable e) {
					model.caught(e);
					return null;
				}
			}, model.clazz, statement, build(model, statement, params));
		}

		/**
		 * ค้นหาข้อมูลในฐานข้อมูลโดยระบุข้อมูลที่ต้องการตามเงื่อนไขที่ระบุ
		 *
		 * @param model
		 *            {@link Model} ของข้อมูล
		 * @param selector
		 *            ตัวระบุข้อมูลจากการค้นหา
		 * @param criteria
		 *            เงื่อนไขในการระบุข้อมูลที่ต้องการค้นหา
		 * @param params
		 *            Parameter ใน <code>criteria</code>
		 * @return ข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
		 * @throws NullPointerException
		 *             <code>model</code> เป็น null
		 * @throws IllegalArgumentException
		 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
		 *             ไม่ถูกต้อง
		 * @throws UnsupportedOperationException
		 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
		 * @see #jpql(Function, Class, CharSequence, Object...)
		 * @see #build(Model, StringBuilder, Object...)
		 * @see TypedQuery#getResultList()
		 */
		protected <S, R> List<R> finds(Model<?> model,
				Selector<S, R> selector,
				CharSequence criteria,
				Object... params)
				throws NullPointerException,
				IllegalArgumentException,
				UnsupportedOperationException {
			StringBuilder statement = new StringBuilder("SELECT ")
					.append(selector.selector(model)).append(" FROM ")
					.append(model.clazz.getSimpleName())
					.append(' ').append(model.as);
			if (criteria != null && criteria.length() > 0) {
				statement.append(" WHERE ").append(criteria);
			}
			if (selector instanceof Statement) {
				if (params == null || params.length == 0) {
					params = new Object[] { selector };
				} else {
					params = Cast.$.array(params, params.length + 1);
					params[params.length - 1] = selector;
				}
			}
			return jpql(query -> {
				try {
					ArrayList<R> list = new ArrayList<>();
					for (S result : query.getResultList()) {
						list.add(selector.result(result));
					}
					return list;
				} catch (Throwable e) {
					model.caught(e);
					return null;
				}
			}, selector.clazz(), statement, build(model, statement, params));
		}

		/**
		 * ล้าง {@link Cache} ใน {@link EntityManagerFactory}
		 *
		 * @param model
		 *            {@link Model} ของข้อมูล
		 * @param id
		 *            ID ของข้อมูลที่ต้องการล้าง {@link Cache} (<code>id</code>
		 *            เป็น null จะล้าง {@link Cache} ทั้ง <code>model</code>)
		 * @return true หากสามารถล้าง {@link Cache} ได้สำเร็จ
		 * @throws NullPointerException
		 *             <code>model</code> เป็น null
		 * @see #factory(Function)
		 * @see #id(Class, Object)
		 * @see Cache#evict(Class)
		 * @see Cache#evict(Class, Object)
		 */
		protected <E> boolean clear(Model<E> model, Object... id)
				throws NullPointerException {
			try {
				return factory(factory -> {
					if (id == null || id.length == 0) {
						factory.getCache().evict(model.clazz);
					} else {
						for (Object i : id) {
							factory.getCache()
									.evict(model.clazz, id(model.clazz, i));
						}
					}
					return true;
				});
			} catch (Throwable e) {
				return false;
			}
		}

		/**
		 * สร้าง {@link EntityManagerFactory}
		 * เพื่อใช้ในการเข้าถึงและปฏิบัติต่อฐานข้อมูล
		 *
		 * @param function
		 *            Functional Interface สำหรับรับเอา
		 *            {@link EntityManagerFactory} ไปดำเนินการต่อ
		 * @return ค่าที่ได้จากการดำเนินการของ <code>function</code>
		 * @throws UnsupportedOperationException
		 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
		 * @see EntityManagerFactory
		 * @see Function#apply(Object)
		 */
		public abstract <R> R factory(
				Function<EntityManagerFactory, R> function)
				throws UnsupportedOperationException;

		/**
		 * สร้าง {@link Model} Object
		 * สำหรับใช้ในการจัดเข้าถึงหรือปฏิบัติต่อฐานข้อมูลตามตารางของ
		 * {@link Entity} Class ที่กำหนด
		 *
		 * @param clazz
		 *            {@link Entity} Clazz ที่ต้องการสร้าง Model
		 * @param alias
		 *            เชื่อแทนของ Entity Name (default "e")
		 * @return {@link Model} Object
		 * @throws ClassCastException
		 *             ไม่สามารถแปล Object ของ {@link Model} ที่สร้างขึ้นเป็น
		 *             Object ของ Sub Class ที่กำหนดได้
		 * @throws NullPointerException
		 *             <code>clazz</code> เป็น null
		 * @see #register
		 * @see Model#Model(Model.Factory, Class, String)
		 */
		public <E, M extends Model<E>> M create(Class<E> clazz, String alias)
				throws ClassCastException, NullPointerException {
			if (register != null) {
				for (Class<? extends Model<?>> register : register) {
					if (Generic.$(register, Model.class, "E") == clazz) {
						try {
							return Cast.$(found(register, alias));
						} catch (Throwable e) {}
					}
				}
			}
			return Cast.$(new Model<>(this, clazz, alias));
		}

		/**
		 * สร้าง {@link Model} Object
		 * สำหรับใช้ในการจัดเข้าถึงหรือปฏิบัติต่อฐานข้อมูลตามตารางของ
		 * {@link Entity} Class ที่กำหนด
		 *
		 * @param clazz
		 *            {@link Entity} Clazz ที่ต้องการสร้าง Model
		 * @return {@link Model} Object
		 * @throws ClassCastException
		 *             ไม่สามารถแปล Object ของ {@link Model} ที่สร้างขึ้นเป็น
		 *             Object ของ Sub Class ที่กำหนดได้
		 * @throws NullPointerException
		 *             <code>clazz</code> เป็น null
		 * @see #create(Class, String)
		 */
		public <E, M extends Model<E>> M create(Class<E> clazz)
				throws ClassCastException, NullPointerException {
			return create(clazz, null);
		}

		/**
		 * สร้าง {@link EntityManager} จาก {@link EntityManagerFactory}
		 * เพื่อใช้ในการเข้าถึงข้อมูลในฐานข้อมูล
		 *
		 * @param function
		 *            Functional Interface ที่จะรับเอา {@link EntityManager}
		 *            ไปดำเนินการต่อและส่งผลจากการดำเนินการกับมา
		 * @return ผลจากการดำเนินการของ <code>function</code>
		 * @throws UnsupportedOperationException
		 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
		 * @see #factory(Function)
		 * @see EntityManagerFactory#createEntityManager()
		 * @see Function#apply(Object)
		 */
		public <R> R manager(Function<EntityManager, R> function)
				throws UnsupportedOperationException {
			return factory(factory -> {
				EntityManager manager = factory.createEntityManager();
				try {
					return function.apply(manager);
				} finally {
					manager.close();
				}
			});
		}

		/**
		 * เปิด {@link EntityTransaction} จาก {@link EntityManager}
		 * เพื่อให้คำสั่งในการปฏิบัติมีผลต่อฐานข้อมูลจริง
		 *
		 * @param function
		 *            Functional Interface ที่จะรับเอา {@link EntityManager}
		 *            ที่เปิด {@link EntityTransaction} แลัว
		 *            ไปดำเนินการต่อและส่งผลจากการดำเนินการกับมา
		 * @return ผลจากการดำเนินการของ <code>function</code>
		 * @throws UnsupportedOperationException
		 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
		 * @see #manager(Function)
		 * @see EntityTransaction#begin()
		 * @see EntityTransaction#commit()
		 * @see Function#apply(Object)
		 */
		public <R> R transaction(Function<EntityManager, R> function)
				throws UnsupportedOperationException {
			return manager(manager -> {
				try {
					manager.setFlushMode(FlushModeType.COMMIT);
					manager.getTransaction().begin();
					return function.apply(manager);
				} finally {
					if (manager.getTransaction().isActive()) {
						if (manager.getTransaction().getRollbackOnly()) {
							manager.getTransaction().rollback();
						} else {
							manager.getTransaction().commit();
						}
					}
				}
			});
		}

		/**
		 * ประมวลคำสั่ง JQPL เพื่อเข้าถึงฐานข้อมูล
		 *
		 * @param function
		 *            Functional Interface ที่จะรับเอา {@link TypedQuery}
		 *            ที่สร้างจาก <code>statement</code> และ <code>params</code>
		 *            ไปดำเนินการต่อ
		 * @param clazz
		 *            Result {@link Class} ของ {@link TypedQuery}
		 * @param statement
		 *            คำสั่ง JPQL สำหรับนำไปสร้างเป็น {@link TypedQuery}
		 * @param params
		 *            Parameter ใน <code>statement</code> สำหรับนำไปสร้างเป็น
		 *            {@link TypedQuery}
		 * @return ผลจากการดำเนินการของ <code>function</code>
		 * @throws NullPointerException
		 *             <code>function</code> หรือ <code>statement</code> เป็น
		 *             null
		 * @throws IllegalArgumentException
		 *             คำสั่ง <code>statement</code> หรือ <code>params</code>
		 *             ไม่ถูกต้อง
		 * @throws UnsupportedOperationException
		 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
		 * @see #manager(Function)
		 * @see #inject(Query, Object...)
		 * @see EntityManager#createQuery(String, Class)
		 * @see Function#apply(Object)
		 */
		public <E, R> R jpql(Function<TypedQuery<E>, R> function,
				Class<E> clazz,
				CharSequence statement,
				Object... params)
				throws NullPointerException,
				IllegalArgumentException,
				UnsupportedOperationException {
			String jpql = statement.toString();
			return manager(manager -> function.apply(
					inject(manager.createQuery(jpql, clazz), params)));
		}

		/**
		 * ประมวลคำสั่ง JQPL เพื่อปฏิบัติต่อฐานข้อมูล
		 *
		 * @param function
		 *            Functional Interface ที่จะรับเอา {@link Query} ที่สร้างจาก
		 *            <code>statement</code> และ <code>params</code>
		 *            ไปดำเนินการต่อ
		 * @param statement
		 *            คำสั่ง JPQL สำหรับนำไปสร้างเป็น {@link Query}
		 * @param params
		 *            Parameter ใน <code>statement</code> สำหรับนำไปสร้างเป็น
		 *            {@link Query}
		 * @return ค่าที่ได้จากการดำเนินการของ <code>function</code>
		 * @throws NullPointerException
		 *             <code>function</code> หรือ <code>statement</code> เป็น
		 *             null
		 * @throws IllegalArgumentException
		 *             คำสั่ง <code>statement</code> หรือ <code>params</code>
		 *             ไม่ถูกต้อง
		 * @throws UnsupportedOperationException
		 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
		 * @see #transaction(Function)
		 * @see #inject(Query, Object...)
		 * @see EntityManager#createQuery(String)
		 * @see Function#apply(Object)
		 */
		public <R> R jpql(Function<Query, R> function,
				CharSequence statement,
				Object... params)
				throws NullPointerException,
				IllegalArgumentException,
				UnsupportedOperationException {
			String jpql = statement.toString();
			return transaction(manager -> function.apply(
					inject(manager.createQuery(jpql), params)));
		}

		/**
		 * ประมวลคำสั่ง SQL เพื่อเข้าถึงกับฐานข้อมูล
		 *
		 * @param function
		 *            Functional Interface ที่จะรับเอา {@link Query} ที่สร้างจาก
		 *            <code>statement</code> และ <code>params</code>
		 *            ไปดำเนินการต่อ
		 * @param clazz
		 *            Result {@link Class} ของ {@link Query}
		 * @param statement
		 *            คำสั่ง SQL สำหรับนำไปสร้างเป็น {@link Query}
		 * @param params
		 *            Parameter ใน <code>statement</code> สำหรับนำไปสร้างเป็น
		 *            {@link Query}
		 * @return ผลจากการดำเนินการของ <code>function</code>
		 * @throws NullPointerException
		 *             <code>function</code> หรือ <code>statement</code> เป็น
		 *             null
		 * @throws IllegalArgumentException
		 *             คำสั่ง <code>statement</code> หรือ <code>params</code>
		 *             ไม่ถูกต้อง
		 * @throws UnsupportedOperationException
		 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
		 * @see #manager(Function)
		 * @see #inject(Query, Object...)
		 * @see EntityManager#createNativeQuery(String, Class)
		 * @see Function#apply(Object)
		 */
		public <E, R> R sql(Function<Query, R> function,
				Class<E> clazz,
				CharSequence statement,
				Object... params)
				throws NullPointerException,
				IllegalArgumentException,
				UnsupportedOperationException {
			String sql = statement.toString();
			return manager(manager -> function.apply(
					inject(manager.createNativeQuery(sql, clazz), params)));
		}

		/**
		 * ประมวลคำสั่ง SQL เพื่อปฏิบัติต่อฐานข้อมูล
		 *
		 * @param function
		 *            Functional Interface ที่จะรับเอา {@link Query} ที่สร้างจาก
		 *            <code>statement</code> และ <code>params</code>
		 *            ไปดำเนินการต่อ
		 * @param statement
		 *            คำสั่ง SQL สำหรับนำไปสร้างเป็น {@link Query}
		 * @param params
		 *            Parameter ใน <code>statement</code> สำหรับนำไปสร้างเป็น
		 *            {@link Query}
		 * @return ผลจากการดำเนินการของ <code>function</code>
		 * @throws NullPointerException
		 *             <code>function</code> หรือ <code>statement</code> เป็น
		 *             null
		 * @throws IllegalArgumentException
		 *             คำสั่ง <code>statement</code> หรือ <code>params</code>
		 *             ไม่ถูกต้อง
		 * @throws UnsupportedOperationException
		 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
		 * @see #transaction(Function)
		 * @see #inject(Query, Object...)
		 * @see EntityManager#createNativeQuery(String)
		 * @see Function#apply(Object)
		 */
		public <R> R sql(Function<Query, R> function,
				CharSequence statement,
				Object... params)
				throws NullPointerException,
				IllegalArgumentException,
				UnsupportedOperationException {
			String sql = statement.toString();
			return transaction(manager -> function.apply(
					inject(manager.createNativeQuery(sql), params)));
		}

		/**
		 * จะล้าง {@link Cache} ทั้งหมดใน {@link EntityManagerFactory}
		 *
		 * @return true หากล้างข้อมูลใน cache ได้สำเร็จ
		 */
		public boolean clear() {
			try {
				return factory(factory -> {
					factory.getCache().evictAll();
					return true;
				});
			} catch (Throwable e) {
				return false;
			}
		}
	}

	/**
	 * Class <code>Model.Pair</code> ใช้สำหรับเก็บคู่อันดับ (Field - Value)
	 *
	 * @since JDK 1.8, jpa-model 2.0
	 * @version 1.0.0
	 * @author เสือไฮ่
	 */
	public static class Pair extends Factory.Pair {
		/**
		 * Class <code>Model.Pair.Series</code> ใช้สำหรับสร้างเก็บ
		 * {@link Model.Pair} หลายตัวต่อๆกัน
		 *
		 * @since JDK 1.8, jpa-model-2.0
		 * @version 1.0.0
		 * @author เสือไฮ่
		 */
		public static class Series implements Iterable<Pair> {
			@Override
			public Iterator<Model.Pair> iterator() {
				return list.iterator();
			}

			/**
			 * {@link Model.Pair} ทั้งหมด
			 */
			protected final ArrayList<Pair> list = new ArrayList<>();

			/**
			 * สร้าง {@link Series} Object โดยใช้ Array Object 2 มิติ
			 * เป็นตัวตั้งต้น<br />
			 * Object[] แต่ละตัวจะหมายถึงคู่อันดับ (Field - Value) หนึ่งคู่ และ
			 * Index ที่ 0 และ 1 ของ Object[]หมายถึง Field และ Value
			 * ของคู่อันดับตามลำดับ
			 *
			 * @param entries
			 *            คู่อันดับ (Field - Value)
			 *            ทั้งหมดที่จะนำไปใช้ในการสร้าง {@link Series}<br />
			 *            {{{@link Pair#field key}, {@link Pair#value value}}[,
			 *            ..., {{@link Pair#field key}, {@link Pair#value
			 *            value}}]}
			 * @throws NullPointerException
			 *             <code>entries</code>, คู่อันดับใดๆ ใน
			 *             <code>entries</code> หรือ <code>field</code>
			 *             ในคู่อันดับเป็น null หรือ ""
			 * @see Pair
			 */
			public Series(Object[]... entries) throws NullPointerException {
				for (Object[] entry : entries) {
					list.add(entry.length == 1
							? new Pair(entry[0].toString(), null)
							: new Pair(entry[0].toString(), entry[1]));
				}
			}

			/**
			 * สร้าง {@link Series} Object
			 *
			 * @param entries
			 *            คู่อันดับ (Field - Value)
			 *            ทั้งหมดที่จะนำไปใช้ในการสร้าง {@link Series}
			 */
			public Series(Pair... entries) {
				list.addAll(Arrays.asList(entries));
			}

			/**
			 * สร้าง {@link Series} โดยการระบุ Field - Value ของคู่อันดับ
			 *
			 * @param field
			 *            {@link Pair#field field}
			 * @param value
			 *            {@link Pair#value value}
			 * @throws NullPointerException
			 *             <code>field</code> เป็น null หรือ ""
			 * @see Pair
			 */
			public Series(String field, Object value)
					throws NullPointerException {
				list.add(new Pair(field, value));
			}

			/**
			 * เพิ่มคู่อันดับ (Field - Value) ใน {@link Pair.Series}
			 * โดยการต่อท้ายคู่อันดับเดิม
			 *
			 * @param field
			 *            {@link Factory.Pair#field field}
			 * @param value
			 *            {@link Factory.Pair#value value}
			 * @return Object ตัวเอง
			 * @throws NullPointerException
			 *             <code>field</code> เป็น null หรือ ""
			 * @see Pair
			 */
			public Series append(String field, Object value)
					throws NullPointerException {
				list.add(new Pair(field, value));
				return this;
			}
		}

		/**
		 * สร้าง {@link Pair} Object
		 *
		 * @param field
		 *            {@link Factory.Pair#field field}
		 * @param value
		 *            {@link Factory.Pair#value value}
		 * @throws NullPointerException
		 *             <code>key</code> เป็น null หรือ ""
		 */
		public Pair(String field, Object value) throws NullPointerException {
			super(field, value);
		}
	}

	/**
	 * Class <code>Model.Criteria</code>
	 * ใช้สำหรับสร้างข้อกำหนดหรือเกณฑ์หรือเงื่อนไขในการระบุข้อมูล
	 *
	 * @since JDK 1.8, jpa-model 2.0
	 * @version 1.0.0
	 * @author เสือไฮ่
	 */
	public static class Criteria extends Factory.Chain {

		/**
		 * สร้าง {@link Model.Criteria} Object
		 */
		public Criteria() {}

		/**
		 * สร้าง {@link Criteria} Object โดยการระบุ {@link Factory.Criteria
		 * Criteria} ตั้งต้น
		 *
		 * @param criteria
		 *            {@link Factory.Criteria Criteria} ตั้งต้น
		 * @throws NullPointerException
		 *             <code>criteria</code> เป็น null
		 * @see #append(CharSequence, Model.Factory.Criteria)
		 */
		public Criteria(Factory.Criteria criteria)
				throws NullPointerException {
			append(null, criteria);
		}

		/**
		 * สร้าง {@link Criteria} Object โดยการระบุ Field, Condition, Value และ
		 * Name ในการสร้าง {@link Factory.Criteria Criteria} ตั้งต้น
		 *
		 * @param field
		 *            {@link Factory.Pair#field Pair.field}
		 * @param condition
		 *            {@link Factory.Logic#condition Logic.condition}
		 * @param value
		 *            {@link Factory.Pair#value Pair.value}
		 * @param name
		 *            {@link Factory.Logic#name Logic.name}
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see #Criteria(Model.Factory.Criteria)
		 * @see Factory.Logic#Logic(String, String, Object, String)
		 *      Factory.Logic
		 */
		public Criteria(
				String field, String condition, Object value, String name)
				throws NullPointerException {
			this(new Factory.Logic(field, condition, value, name));
		}

		/**
		 * สร้าง {@link Criteria} Object โดยการระบุ Field, Condition, และ Value
		 * ในการสร้าง {@link Factory.Criteria Criteria} ตั้งต้น
		 *
		 * @param field
		 *            {@link Factory.Logic#field Pair.field}
		 * @param condition
		 *            {@link Factory.Logic#condition Logic.condition}
		 * @param value
		 *            {@link Factory.Pair#value Pair.value}
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see #Criteria(String, String, Object, String)
		 */
		public Criteria(String field, String condition, Object value)
				throws NullPointerException {
			this(field, condition, value, null);
		}

		/**
		 * สร้าง {@link Criteria} Object โดยการระบุ Field, Value และ Name
		 * ในการสร้าง {@link Factory.Criteria Criteria} ตั้งต้น
		 *
		 * @param field
		 *            {@link Factory.Pair#field Pair.field}
		 * @param value
		 *            {@link Factory.Pair#value Pair.value}
		 * @param name
		 *            {@link Factory.Logic#name Logic.name}
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see #Criteria(String, String, Object, String)
		 */
		public Criteria(String field, Object value, String name)
				throws NullPointerException {
			this(field, null, value, name);
		}

		/**
		 * สร้าง {@link Criteria} Object โดยการระบุ Field และ Value ในการสร้าง
		 * {@link Factory.Criteria Criteria} ตั้งต้น
		 *
		 * @param field
		 *            {@link Factory.Pair#field Pair.field}
		 * @param value
		 *            {@link Factory.Pair#value Pair.value}
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see #Criteria(String, String, Object, String)
		 */
		public Criteria(String field, Object value)
				throws NullPointerException {
			this(field, null, value, null);
		}

		/**
		 * เชื่อม {@link Factory.Criteria Criteria} ที่ต้องการด้วยวิธี AND
		 *
		 * @param criteria
		 *            {@link Factory.Criteria Criteria} ที่ต้องการเชื่อม
		 * @return Object ตัวเอง
		 * @throws NullPointerException
		 *             <code>criteria</code> เป็น null
		 * @see #append(CharSequence, Model.Factory.Criteria)
		 */
		public Criteria and(Factory.Criteria criteria)
				throws NullPointerException {
			append("AND", criteria);
			return this;
		}

		/**
		 * สร้าง {@link Factory.Logic Logic} โดยใช้ Field, Condition, Value และ
		 * Name และเชื่อมกับ {@link Factory.Criteria Criteria} เดิมด้วยวิธี AND
		 *
		 * @param field
		 *            {@link Factory.Pair#field Pair.field}
		 * @param condition
		 *            {@link Factory.Logic#condition Logic.condition}
		 * @param value
		 *            {@link Factory.Pair#value Pair.value}
		 * @param name
		 *            {@link Factory.Logic#name Logic.name}
		 * @return Object ตัวเอง
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see #and(Model.Factory.Criteria)
		 * @see Model.Factory.Logic#Logic(String, String, Object, String)
		 *      Model.Factory.Logic
		 */
		public Criteria and(
				String field, String condition, Object value, String name)
				throws NullPointerException {
			return and(new Factory.Logic(field, condition, value, name));
		}

		/**
		 * สร้าง {@link Factory.Logic Logic} โดยใช้ Field, Condition และ Value
		 * และเชื่อมกับ {@link Factory.Criteria Criteria} เดิมด้วยวิธี AND
		 *
		 * @param field
		 *            {@link Factory.Pair#field Pair.field}
		 * @param condition
		 *            {@link Factory.Logic#condition Logic.condition}
		 * @param value
		 *            {@link Factory.Pair#value Pair.value}
		 * @return Object ตัวเอง
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see #and(String, String, Object, String)
		 */
		public Criteria and(String field, String condition, Object value)
				throws NullPointerException {
			return and(field, condition, value, null);
		}

		/**
		 * สร้าง {@link Factory.Logic Logic} โดยใช้ Field, Value และ Name
		 * และเชื่อมกับ {@link Factory.Criteria Criteria} เดิมด้วยวิธี AND
		 *
		 * @param field
		 *            {@link Factory.Pair#field Pair.field}
		 * @param value
		 *            {@link Factory.Pair#value Pair.value}
		 * @param name
		 *            {@link Factory.Logic#name Logic.name}
		 * @return Object ตัวเอง
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see #and(String, String, Object, String)
		 */
		public Criteria and(String field, Object value, String name)
				throws NullPointerException {
			return and(field, null, value, name);
		}

		/**
		 * สร้าง {@link Factory.Logic Logic} โดยใช้ Field และ Value และเชื่อมกับ
		 * {@link Factory.Criteria Criteria} เดิมด้วยวิธี AND
		 *
		 * @param field
		 *            {@link Factory.Pair#field Pair.field}
		 * @param value
		 *            {@link Factory.Pair#value Pair.value}
		 * @return Object ตัวเอง
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see #and(String, String, Object, String)
		 */
		public Criteria and(String field, Object value)
				throws NullPointerException {
			return and(field, null, value, null);
		}

		/**
		 * เชื่อม {@link Factory.Criteria Criteria} ที่ต้องการด้วยวิธี OR
		 *
		 * @param criteria
		 *            {@link Factory.Criteria Criteria} ที่ต้องการเชื่อม
		 * @return Object ตัวเอง
		 * @throws NullPointerException
		 *             <code>criteria</code> เป็น null
		 * @see #append(CharSequence, Model.Factory.Criteria)
		 */
		public Criteria or(Factory.Criteria criteria)
				throws NullPointerException {
			append("OR", criteria);
			return this;
		}

		/**
		 * สร้าง {@link Factory.Logic Logic} โดยใช้ Field, Condition, Value และ
		 * Name และเชื่อมกับ {@link Factory.Criteria Criteria} เดิมด้วยวิธี OR
		 *
		 * @param field
		 *            {@link Factory.Pair#field Pair.field}
		 * @param condition
		 *            {@link Factory.Logic#condition Logic.condition}
		 * @param value
		 *            {@link Factory.Pair#value Pair.value}
		 * @param name
		 *            {@link Factory.Logic#name Logic.name}
		 * @return Object ตัวเอง
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see #or(Model.Factory.Criteria)
		 * @see Model.Factory.Logic#Logic(String, String, Object, String)
		 */
		public Criteria or(
				String field, String condition, Object value, String name)
				throws NullPointerException {
			return or(new Factory.Logic(field, condition, value, name));
		}

		/**
		 * สร้าง {@link Factory.Logic Logic} โดยใช้ Field, Condition และ Value
		 * และเชื่อมกับ {@link Factory.Criteria Criteria} เดิมด้วยวิธี OR
		 *
		 * @param field
		 *            {@link Factory.Pair#field Pair.field}
		 * @param condition
		 *            {@link Factory.Logic#condition Logic.condition}
		 * @param value
		 *            {@link Factory.Pair#value Pair.value}
		 * @return Object ตัวเอง
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see #or(String, String, Object, String)
		 */
		public Criteria or(String field, String condition, Object value)
				throws NullPointerException {
			return or(field, condition, value, null);
		}

		/**
		 * สร้าง {@link Factory.Logic Logic} โดยใช้ Field, Value และ Name
		 * และเชื่อมกับ {@link Factory.Criteria Criteria} เดิมด้วยวิธี OR
		 *
		 * @param field
		 *            {@link Factory.Pair#field Pair.field}
		 * @param value
		 *            {@link Factory.Pair#value Pair.value}
		 * @param name
		 *            {@link Factory.Logic#name Logic.name}
		 * @return Object ตัวเอง
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see #or(String, String, Object, String)
		 */
		public Criteria or(String field, Object value, String name)
				throws NullPointerException {
			return or(field, null, value, name);
		}

		/**
		 * สร้าง {@link Factory.Logic Logic} โดยใช้ Field และ Value และเชื่อมกับ
		 * {@link Factory.Criteria Criteria} เดิมด้วยวิธี OR
		 *
		 * @param field
		 *            {@link Factory.Pair#field Pair.field}
		 * @param value
		 *            {@link Factory.Pair#value Pair.value}
		 * @return Object ตัวเอง
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see #or(String, String, Object, String)
		 */
		public Criteria or(String field, Object value)
				throws NullPointerException {
			return or(field, null, value, null);
		}

		/**
		 * กำหนดความเป็นนิเสธให้กับ {@link Criteria} ทั้งประโยค
		 *
		 * @param active
		 *            true : จะกำหนดให้ประโยคนี้เป็นนิเสธ, false
		 *            จะกำหนดให้เป็นประโยคปรกติ
		 * @return Object ตัวเอง
		 * @see #negation
		 */
		public Criteria not(boolean active) {
			negation = active;
			return this;
		}

		/**
		 * กำหนดความเป็นนิเสธให้กับ {@link Criteria} ทั้งประโยค
		 *
		 * @return Object ตัวเอง
		 * @see #not(boolean)
		 */
		public Criteria not() {
			return not(true);
		}
	}

	/**
	 * Class <code>Model.Logic</code> สำหรับสร้าง {@link Criteria}
	 * โดยการเปรียบเทียบค่าของข้อมูล ณ Field ที่กำหนด กับ Value ที่ระบุ
	 *
	 * @since JDK 1.8, jpa-model-2.0
	 * @version 1.0.0
	 * @author เสือไฮ่
	 */
	public static class Logic extends Factory.Logic {
		/**
		 * สร้าง {@link Logic} Object
		 *
		 * @param field
		 *            {@link Factory.Logic#field field}
		 * @param condition
		 *            {@link Factory.Logic#condition condition}
		 * @param value
		 *            {@link Factory.Logic#value value}
		 * @param name
		 *            {@link Factory.Logic#name name}
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see Model.Factory.Logic#Logic(String, String, Object, String)
		 */
		public Logic(String field, String condition, Object value, String name)
				throws NullPointerException {
			super(field, condition, value, name);
		}

		/**
		 * สร้าง {@link Logic} Object
		 *
		 * @param field
		 *            {@link Factory.Logic#field field}
		 * @param condition
		 *            {@link Factory.Logic#condition condition}
		 * @param value
		 *            {@link Factory.Logic#value value}
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see Model.Factory.Logic#Logic(String, String, Object, String)
		 */
		public Logic(String field, String condition, Object value)
				throws NullPointerException {
			super(field, condition, value, null);
		}

		/**
		 * สร้าง {@link Logic} Object
		 *
		 * @param field
		 *            {@link Factory.Logic#field field}
		 * @param value
		 *            {@link Factory.Logic#value value}
		 * @param name
		 *            {@link Factory.Logic#name name}
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see Model.Factory.Logic#Logic(String, String, Object, String)
		 */
		public Logic(String field, Object value, String name)
				throws NullPointerException {
			super(field, null, value, name);
		}

		/**
		 * สร้าง {@link Logic} Object
		 *
		 * @param field
		 *            {@link Factory.Logic#field field}
		 * @param value
		 *            {@link Factory.Logic#value value}
		 * @throws NullPointerException
		 *             <code>field</code> เป็น null หรือ ""
		 * @see Model.Factory.Logic#Logic(String, String, Object, String)
		 */
		public Logic(String field, Object value) throws NullPointerException {
			super(field, null, value, null);
		}
	}

	/**
	 * Class <code>Model.Aggregate</code> สำหรับใช้ในการประมวลผล Aggregate
	 * Function
	 *
	 * @author เสือไฮ่
	 */
	public static class Aggregate implements Model.Factory.Statement,
		Model.Factory.Selector<Object[], Map<String, Object>> {
		/**
		 * Field สำหรับ สร้าง Expression "GROUP BY"
		 */
		private final String[] fields;
		/**
		 * Aggregate Function
		 */
		private final Map<String, String> with = new LinkedHashMap<>();

		/**
		 * Constructor สำหรับสร้าง {@link Aggregate} Object
		 *
		 * @param fields
		 *            {@link #fields}
		 * @throws NullPointerException
		 *             <code>fields</code> เป็น null หรือ
		 *             <code>fields.length</code> = 0 หรือ เป็น Empty String
		 */
		public Aggregate(String... fields) throws NullPointerException {
			if ((this.fields = fields) == null || fields.length == 0)
				throw new NullPointerException();
		}

		/**
		 * Constructor สำหรับสร้าง {@link Aggregate} Object
		 *
		 * @param fields
		 *            {@link #fields}
		 * @throws NullPointerException
		 *             <code>fields</code> เป็น null หรือ
		 *             <code>fields.length</code> = 0 หรือ เป็น Empty String
		 * @see String#split(String)
		 */
		public Aggregate(String fields) throws NullPointerException {
			this(fields.split(" *, *"));
		}

		/**
		 * Aggregate Function
		 *
		 * @param key
		 *            ชื่อใน Map Result
		 * @param value
		 *            คำสั่ง Aggregate Function
		 * @return Object ตัวเอง
		 */
		public Aggregate with(String key, String value) {
			with.put(key, value);
			return this;
		}

		@Override
		public CharSequence selector(Model<?> model) {
			StringBuilder builder = new StringBuilder();
			for (String field : fields) {
				builder.append(model.ialias(field)).append(", ");
			}
			for (String value : with.values()) {
				builder.append(model.ialias(value)).append(", ");
			}
			return builder.delete(builder.length() - 2, builder.length());
		}

		@Override
		public Map<String, Object> result(Object[] result) {
			HashMap<String, Object> map = new HashMap<>();
			for (int i = 0; i < fields.length; i++) {
				map.put(fields[i], result[i]);
			}
			String[] keys = Cast.$.array(String.class, with.keySet());
			for (int i = 0; i < keys.length; i++) {
				map.put(keys[i], result[i + fields.length]);
			}
			return map;
		}

		@Override
		public void build(Model<?> model,
				StringBuilder statement,
				Map<String, Object> named,
				List<Object> index)
				throws NullPointerException, IllegalArgumentException {
			group(fields).build(model, statement, named, index);
		}
	}

	/**
	 * Class <code>Model.CriteriaBuilder</code> เป็น Class
	 * สำหรับสร้างคำสั่งระบุเงื่อนไขจาก {@link Criteria} Object
	 *
	 * @since JDK 1.8, jpa-model 2.0
	 * @version 1.0.0
	 * @author เสือไฮ่
	 */
	protected class CriteriaBuilder {
		/**
		 * คำสั่งระบุเงื่อนไข
		 */
		protected final StringBuilder criteria = new StringBuilder();
		/**
		 * Parameter ใน {@link #criteria} รวมกับ Parameter อื่นๆ
		 * เพิ่มเติมมาทีหลัง
		 */
		protected final Object[] params;

		/**
		 * สร้าง {@link CriteriaBuilder} Object
		 *
		 * @param criteria
		 *            {@link #criteria}
		 * @param params
		 *            Parameter อื่นนอกจาก Parameter ใน <code>criteria</code>
		 * @throws NullPointerException
		 *             <code>model</code> หรือ <code>criteria</code> เป็น null
		 * @throws IllegalArgumentException
		 *             <code>criteria</code> หรือ <code>params</code> ไม่ถูกต้อง
		 */
		public CriteriaBuilder(Factory.Criteria criteria, Object... params)
				throws NullPointerException, IllegalArgumentException {
			if (params != null && params.length > 0
					&& params[0] instanceof Map) {
				Map<String, Object> init = Cast.$(params[0]);
				criteria.build(Model.this, this.criteria, init);
				this.params = params;
			} else if (criteria.isNaming()) {
				HashMap<String, Object> init = new HashMap<>();
				criteria.build(Model.this, this.criteria, init);
				if (params == null || params.length == 0) {
					this.params = new Object[] { init };
				} else {
					(this.params = new Object[params.length + 1])[0] = init;
					System.arraycopy(params, 0, this.params, 1, params.length);
				}
			} else {
				ArrayList<Object> init = new ArrayList<>();
				criteria.build(Model.this, this.criteria, init);
				if (params != null) {
					init.addAll(Arrays.asList(params));
				}
				this.params = init.toArray();
			}
		}

		/**
		 * สร้าง {@link CriteriaBuilder} Object
		 *
		 * @param criteria
		 *            {@link #criteria}
		 * @param init
		 *            Parameter ตั้งตั้งต้นก่อนจะถึง Parameter ใน
		 *            <code>criteria</code>
		 * @param ext
		 *            Parameter หลังจาก Parameter ใน <code>criteria</code>
		 * @throws NullPointerException
		 *             <code>model</code> หรือ <code>criteria</code> เป็น null
		 * @throws IllegalArgumentException
		 *             <code>criteria</code> หรือ <code>init</code> ไม่ถูกต้อง
		 */
		public CriteriaBuilder(Factory.Criteria criteria,
				Map<String, Object> init,
				Object... ext)
				throws NullPointerException,
				IllegalArgumentException {
			if (init == null) {
				init = new HashMap<>();
			}
			criteria.build(Model.this, this.criteria, init);
			if (ext == null || ext.length == 0) {
				params = new Object[] { init };
			} else {
				(params = new Object[ext.length + 1])[0] = init;
				System.arraycopy(ext, 0, params, 1, ext.length);
			}
		}

		/**
		 * สร้าง {@link CriteriaBuilder} Object
		 *
		 * @param criteria
		 *            {@link #criteria}
		 * @param init
		 *            Parameter ตั้งตั้งต้นก่อนจะถึง Parameter ใน
		 *            <code>criteria</code>
		 * @param ext
		 *            Parameter หลังจาก Parameter ใน <code>criteria</code>
		 * @throws NullPointerException
		 *             <code>model</code> หรือ <code>criteria</code> เป็น null
		 * @throws IllegalArgumentException
		 *             <code>criteria</code> หรือ <code>init</code> ไม่ถูกต้อง
		 */
		public CriteriaBuilder(Factory.Criteria criteria,
				List<Object> init,
				Object... ext)
				throws NullPointerException,
				IllegalArgumentException {
			if (init == null) {
				init = new ArrayList<>();
			}
			criteria.build(Model.this, this.criteria, init);
			if (ext == null || ext.length == 0) {
				params = init.toArray();
			} else {
				params = init.toArray(new Object[init.size() + ext.length]);
				System.arraycopy(ext, 0, params, init.size(), ext.length);
			}
		}
	}

	/**
	 * Class <code>Model.ValueBuilder</code>
	 * ใช้สำหรับสร้างคำสั่งในการกำหนดค่าให้กับข้อมูลในฐาน
	 *
	 * @since JDK 1.8, jpa-model-2.0
	 * @version 1.0.0
	 * @author เสือไฮ่
	 */
	protected class ValueBuilder {
		/**
		 * คำสั่งกำหนดค่า
		 */
		protected final CharSequence values;
		/**
		 * คำสั่งระบุเงื่อนไข
		 */
		protected final CharSequence criteria;
		/**
		 * Parameter ในคำสั่ง {@link #values}, {@link #criteria} รวมกับ
		 * Parameter อื่นๆ เพิ่มเติมมาทีหลัง
		 */
		protected final Object[] params;

		/**
		 * สร้าง {@link ValueBuilder} Object
		 *
		 * @param values
		 *            {@link #values}
		 * @param criteria
		 *            {@link #criteria}
		 * @param params
		 *            {@link #params}
		 * @throws NullPointerException
		 *             <code>model</code> เป็น null
		 * @throws IllegalArgumentException
		 *             <code>model</code>, <code>criteria</code> หรือ
		 *             <code>params</code> ไม่ถูกต้อง
		 * @see Model.CriteriaBuilder
		 */
		public ValueBuilder(
				Pair.Series values, Factory.Criteria criteria, Object[] params)
				throws NullPointerException, IllegalArgumentException {
			Model<E>.CriteriaBuilder builder;
			if (params != null && params.length > 0
					&& params[0] instanceof Map) {
				Map<String, Object> init = Cast.$(params[0]);
				this.values = init(values, init);
				if (criteria == null) {
					this.criteria = null;
					this.params = params;
					return;
				} else if (params.length == 1) {
					builder = new CriteriaBuilder(criteria, init);
				} else {
					Object[] ext = new Object[params.length - 1];
					System.arraycopy(params, 1, ext, 0, ext.length);
					builder = new CriteriaBuilder(criteria, init, ext);
				}
			} else if (criteria == null) {
				ArrayList<Object> init = new ArrayList<>();
				this.values = init(values, init);
				this.criteria = null;
				this.params = new Object[] { init };
				return;
			} else if (criteria.isNaming()) {
				HashMap<String, Object> init = new HashMap<>();
				this.values = init(values, init);
				builder = new CriteriaBuilder(criteria, init, params);
			} else {
				ArrayList<Object> init = new ArrayList<>();
				this.values = init(values, init);
				builder = new CriteriaBuilder(criteria, init, params);
			}
			this.criteria = builder.criteria;
			this.params = builder.params;
		}

		/**
		 * สร้างคำสั่งกำหนดค่า
		 *
		 * @param series
		 *            value ทั้งหมด
		 * @param params
		 *            ค่าที่จะกำหนดใน Field
		 * @return คำสั่งกำหนดค่า
		 */
		protected CharSequence init(
				Pair.Series series, Map<String, Object> params) {
			StringBuilder builder = new StringBuilder();
			for (Pair i : series) {
				params.put(i.field.replace('.', '_'), i.value);
				builder.append(", ").append(ialias(i.field))
						.append(" = :").append(i.field.replace('.', '_'));
			}
			return builder.delete(0, 2);
		}

		/**
		 * สร้างคำสั่งกำหนดค่า
		 *
		 * @param series
		 *            value ทั้งหมด
		 * @param params
		 *            ค่าที่จะกำหนดใน Field
		 * @return คำสั่งกำหนดค่า
		 */
		protected CharSequence init(Pair.Series series, List<Object> params) {
			StringBuilder builder = (StringBuilder) values;
			for (Pair i : series) {
				params.add(i.value);
				builder.append(", ").append(ialias(i.field))
						.append(" = ?").append(params.size());
			}
			return builder.delete(0, 2);
		}
	}

	/**
	 * Function สำหรับสร้าง {@link Criteria} Object
	 *
	 * @param field
	 *            {@link Factory.Pair#field Pair.field}
	 * @param value
	 *            {@link Factory.Pair#value Pair.value}
	 * @return {@link Criteria} Object ที่สร้างขึ้น
	 * @see Criteria#Criteria(String, Object) Criteria
	 */
	public static Criteria cri(String field, Object value) {
		return new Criteria(field, value);
	}

	/**
	 * Function สำหรับสร้างตัวกำหนด Expression "GROUP BY"
	 *
	 * @param fields
	 *            ชื่อ Column ที่ต้องการแบ่งกลุ่มในคำสั่ง GROUP BY
	 * @return ตัวกำหนด Expression "GROUP BY"
	 */
	public static Factory.Statement group(String... fields) {
		if (fields == null || fields.length == 0)
			throw new NullPointerException();
		return (model, statement, named, index) -> {
			statement.append(" GROUP BY ");
			for (String field : fields) {
				statement.append(model.ialias(field)).append(", ");
			}
			statement.delete(statement.length() - 2, statement.length());
		};
	}

	/**
	 * Function สำหรับสร้างตัวกำหนด Expression "GROUP BY"
	 *
	 * @param fields
	 *            ชื่อ Column ที่ต้องการแบ่งกลุ่มในคำสั่ง GROUP BY
	 * @return ตัวกำหนด Expression "GROUP BY"
	 */
	public static Factory.Statement group(String fields) {
		return group(fields.split(" *, *"));
	}

	/**
	 * Function สำหรับสร้าง {@link Aggregate} Object
	 *
	 * @param fields
	 *            {@link Aggregate#fields}
	 * @return {@link Aggregate} Object ที่สร้างขึ้น
	 * @see Aggregate#Aggregate(String) Aggregate
	 */
	public static Aggregate agg(String... fields) {
		return new Aggregate(fields);
	}

	/**
	 * Function สำหรับสร้าง {@link Aggregate} Object
	 *
	 * @param fields
	 *            {@link Aggregate#fields}
	 * @return {@link Aggregate} Object ที่สร้างขึ้น
	 * @see Aggregate#Aggregate(String) Aggregate
	 */
	public static Aggregate agg(String fields) {
		return new Aggregate(fields);
	}

	/**
	 * Function สำหรับสร้างตัวกำหนด Expression "HAVING"
	 *
	 * @param criteria
	 *            {@link Factory.Criteria Criteria} ใน Expression "HAVING"
	 * @return ตัวกำหนด Expression "HAVING"
	 */
	public static Factory.Statement having(Factory.Criteria criteria) {
		return (model, statement, named, index) -> {
			criteria.build(model, statement, named, index);
		};
	}

	/**
	 * Function สำหรับสร้างตัวกำหนด Expression "HAVING"
	 *
	 * @param criteria
	 *            Criteria ใน Expression "HAVING"
	 * @param params
	 *            Parameter ใน <code>criteria</code>
	 * @return ตัวกำหนด Expression "HAVING"
	 */
	public static Factory.Statement having(
			CharSequence criteria, Object... params) {
		return (model, statement, named, index) -> {
			if (params == null || params.length == 0) {
				statement.append(criteria);
				return;
			}
			if (named != null) {
				if (params.length == 1 && params[0] instanceof Map) {
					named.putAll(Cast.$(params[0]));
				} else {
					try {
						Matcher matcher = Pattern.compile(
								":((?:\\w|\\d|[_$])+)").matcher(criteria);
						for (int i = 0; matcher.find();) {
							if (!named.containsKey(matcher.group(1))) {
								named.put(matcher.group(1), params[i++]);
							}
						}
					} catch (ArrayIndexOutOfBoundsException e) {}
					statement.append(criteria);
				}
			} else if (index != null) {
				Matcher matcher = Pattern.compile("[?](\\d+)?")
						.matcher(criteria);
				StringBuffer buffer = new StringBuffer();
				for (int i = 0; matcher.find();) {
					try {
						int at = Integer.parseInt(matcher.group(1));
						if (index.size() + 1 == at) {
							index.add(params[i++]);
						} else if (index.size() < at) {
							index.set(at, params[i++]);
						}
					} catch (NumberFormatException e) {
						index.add(params[i++]);
						matcher.appendReplacement(buffer, "?" + index.size());
					}
				}
				statement.append(buffer.length() == 0
						? criteria : matcher.appendTail(buffer));
			} else throw new IllegalArgumentException();
		};
	}

	/**
	 * Function สำหรับสร้างตัวกำหนด Expression "ORDER BY"
	 *
	 * @param fields
	 *            ชื่อ Column ที่ต้องการเรียงในคำสั่ง "ORDER BY" และวิธีการเรียง
	 *            (ASC, DESC)
	 * @return ตัวกำหนด Expression "ORDER BY"
	 */
	public static Factory.Statement order(String... fields) {
		if (fields == null || fields.length == 0)
			throw new NullPointerException();
		return (model, statement, named, index) -> {
			statement.append(" ORDER BY ");
			for (String field : fields) {
				statement.append(model.ialias(field)).append(", ");
			}
			statement.delete(statement.length() - 2, statement.length());
		};
	}

	/**
	 * Function สำหรับสร้างตัวกำหนด Expression "ORDER BY"
	 *
	 * @param fields
	 *            ชื่อ Column ที่ต้องการเรียงในคำสั่ง "ORDER BY" และวิธีการเรียง
	 *            (ASC, DESC)
	 * @return ตัวกำหนด Expression "ORDER BY"
	 */
	public static Factory.Statement order(String fields) {
		return order(fields.split(" *, *"));
	}

	/**
	 * Function สำหรับสร้างตัวกำหนดการทดหรือชดเชยจำนวนผลลัพธ์จากการ Query
	 *
	 * @param value
	 *            จำนวนผลลัพธ์ที่ต้องการกำหนดในการทดหรือชดเชย
	 * @return ตัวกำหนดการทดหรือชดเชยจำนวนผลลัพธ์
	 */
	public static Factory.Injector offset(int value) {
		return query -> query.setFirstResult(value);
	}

	/**
	 * Function สำหรับสร้างตัวกำหนดการจำกัดจำนวนผลลัพธ์จากการ Query
	 *
	 * @param value
	 *            จำนวนผลลัพธ์ที่ต้องการกำหนดในการจำกัด
	 * @return ตัวกำหนดการจำกัดจำนวนผลลัพธ์
	 */
	public static Factory.Injector limit(int value) {
		return query -> query.setMaxResults(value);
	}

	/**
	 * สร้างคำสั่ง Sub Query Statement
	 * 
	 * @param statement
	 *            คำสั่ง Sub Query Statement
	 * @param params
	 *            Parameter ในคำสั่ง Sub Query Statement
	 * @return คำสั่ง Sub Query
	 */
	public static Factory.Statement sub(
			CharSequence statement, Object... params) {
		return (model, builder, named, index) -> {
			if (params == null || params.length == 0) {
				builder.append(statement);
			} else if (named != null) {
				if (params.length == 1 && params[0] instanceof Map) {
					named.putAll(Cast.$(params[0]));
				} else {
					throw new IllegalArgumentException();
				}
				builder.append(statement);
			} else if (index != null) {
				Matcher matcher = Pattern.compile("\\?\\d*").matcher(statement);
				StringBuffer buffer = new StringBuffer();
				try {
					Iterator<Object> i = Arrays.asList(params).iterator();
					while (matcher.find()) {
						index.add(i.next());
						matcher.appendReplacement(buffer, "?" + index.size());
					}
					if (i.hasNext()) {
						throw new IllegalArgumentException();
					}
				} catch (NoSuchElementException e) {
					throw new IllegalArgumentException();
				}
				builder.append(matcher.appendTail(buffer));
			}
		};
	}

	/**
	 * สร้างคำสั่ง SQL สำหรับใส่ลงใน JPQL
	 *
	 * @param statement
	 *            คำสั่ง SQL
	 * @param params
	 *            Parameter ในคำสั่ง SQL
	 * @return ตัวกำหนดการจำกัดจำนวนผลลัพธ์
	 */
	public static Factory.Statement sql(
			CharSequence statement, Object... params) {
		return (model, builder, named, index) -> {
			builder.append("SQL('").append(statement).append('\'');
			if (params != null && params.length > 0) {
				if (named != null) {
					if (params.length == 1 && params[0] instanceof Map) {
						for (Map.Entry<?, ?> entry : ((Map<?, ?>) params[0])
								.entrySet()) {
							Object val = entry.getValue();
							if (val instanceof Factory.Statement) {
								((Factory.Statement) val)
										.build(model, builder, named, index);
							} else {
								String name = entry.getKey() == null ? null
										: entry.getKey().toString();
								builder.append(", :").append(name);
								if (val != null || !named.containsKey(name)) {
									Object release = named.put(name, val);
									if (release != null
											&& !release.equals(val)) {
										throw new IllegalArgumentException(
												"Parameter \"" + name
														+ "\" was conflict");
									}
								}
							}
						}
					}
				} else if (index != null) {
					int stamp = builder.length();
					for (Object param : params) {
						if (param instanceof Factory.Statement) {
							((Model.Factory.Statement) param).build(
									model, builder.append(", "), named, index);
						} else if (param == null) {
							builder.append(", NULL");
						} else {
							index.add(param);
							builder.append(", ?").append(index.size());
						}
					}
					builder.delete(stamp, stamp + 2);
				}
			}
			builder.append(")");
		};
	}

	/**
	 * Core ในการเชื่อมต่อฐานข้อมูลของ {@link Model}
	 */
	public final Factory factory;
	/**
	 * {@link Entity} Class ของตารางในฐานข้อมูลที่ {@link Model}
	 * เชื่อมต่อข้อมูลอยู่
	 */
	public final Class<E> clazz;
	/**
	 * ชื่อแทนสำหรับใช้ในคำสั่ง JPQL ในการเข้าถึงหรือปฏิบัติต่อฐานข้อมูล
	 */
	public final String as;
	/**
	 * ตัวรอรับเหตุการณ์ที่เกิดขึ้นใน {@link Model}
	 */
	public final Listener<Model<E>> listener;

	/**
	 * สร้าง {@link Model} Object
	 *
	 * @param factory
	 *            {@link #factory}
	 * @param alias
	 *            {@link #as}
	 * @throws NullPointerException
	 *             <code>factory</code> เป็น null
	 * @throws IllegalArgumentException
	 *             หรือไม่ได้กำหนด Generic Class {@code <}E{@code >}
	 * @see Generic#find(Class, String)
	 */
	protected Model(Factory factory, String alias)
			throws NullPointerException, IllegalArgumentException {
		if ((clazz = new Generic(getClass()).find(Model.class, "E")) == null)
			throw new IllegalArgumentException();
		else if ((this.factory = factory) == null)
			throw new NullPointerException();
		else if (alias == null || alias.isEmpty()) {
			Alias anno = clazz.getAnnotation(Alias.class);
			this.as = anno == null || anno.value().isEmpty()
					? "e" : anno.value();
		} else {
			this.as = alias;
		}
		listener = new Listener<>(this, Throwable.class);
	}

	/**
	 * สร้าง {@link Model} Object
	 *
	 * @param factory
	 *            {@link #factory}
	 * @throws NullPointerException
	 *             <code>factory</code> เป็น null
	 * @throws IllegalArgumentException
	 *             หรือไม่ได้กำหนด Generic Class {@code <}E{@code >}
	 * @see #Model(Factory, String)
	 * @see Alias
	 */
	protected Model(Factory factory)
			throws NullPointerException, IllegalArgumentException {
		this(factory, (String) null);
	}

	/**
	 * สร้าง {@link Model} Object
	 *
	 * @param factory
	 *            {@link #factory}
	 * @param clazz
	 *            {@link #clazz}
	 * @param alias
	 *            {@link #as}
	 * @throws NullPointerException
	 *             <code>factory</code> หรือ <code>clazz</code> เป็น null
	 */
	protected Model(Factory factory, Class<E> clazz, String alias)
			throws NullPointerException {
		if ((this.factory = factory) == null || (this.clazz = clazz) == null)
			throw new NullPointerException();
		else if (alias == null || alias.isEmpty()) {
			Alias anno = clazz.getAnnotation(Alias.class);
			this.as = anno == null ? "e" : anno.value();
		} else {
			this.as = alias;
		}
		listener = new Listener<>(this, Throwable.class);
	}

	/**
	 * สร้าง {@link Model} Object
	 *
	 * @param factory
	 *            {@link #factory}
	 * @param clazz
	 *            {@link #clazz}
	 * @throws NullPointerException
	 *             <code>factory</code> หรือ <code>clazz</code> เป็น null
	 * @see #Model(Factory, Class, String)
	 * @see Alias
	 */
	protected Model(Factory factory, Class<E> clazz)
			throws NullPointerException {
		this(factory, clazz, null);
	}

	/**
	 * สร้าง {@link Model} Object
	 *
	 * @param factory
	 *            {@link #factory}
	 * @param alias
	 *            {@link #as}
	 * @throws NullPointerException
	 *             <code>factory</code> เป็น null
	 * @throws IllegalArgumentException
	 *             หรือไม่ได้กำหนด Generic Class {@code <}E{@code >}
	 * @see #Model(Factory, String)
	 * @see Factory.Static#Static(EntityManagerFactory, Class...) Factory.Static
	 */
	protected Model(EntityManagerFactory factory, String alias)
			throws NullPointerException, IllegalArgumentException {
		this(new Factory.Static(factory), alias);
	}

	/**
	 * สร้าง {@link Model} Object
	 *
	 * @param factory
	 *            {@link #factory}
	 * @throws NullPointerException
	 *             <code>factory</code> เป็น null
	 * @throws IllegalArgumentException
	 *             หรือไม่ได้กำหนด Generic Class {@code <}E{@code >}
	 * @see #Model(EntityManagerFactory, String)
	 * @see Alias
	 */
	protected Model(EntityManagerFactory factory)
			throws NullPointerException, IllegalArgumentException {
		this(factory, (String) null);
	}

	/**
	 * สร้าง {@link Model} Object
	 *
	 * @param factory
	 *            {@link #factory}
	 * @param clazz
	 *            {@link #clazz}
	 * @param alias
	 *            {@link #as}
	 * @throws NullPointerException
	 *             <code>factory</code> หรือ <code>clazz</code> เป็น null
	 * @see #Model(Factory, Class, String)
	 * @see Factory.Static#Static(EntityManagerFactory, Class...) Factory.Static
	 */
	public Model(EntityManagerFactory factory, Class<E> clazz, String alias)
			throws NullPointerException {
		this(new Factory.Static(factory), clazz, alias);
	}

	/**
	 * สร้าง {@link Model} Object
	 *
	 * @param factory
	 *            {@link #factory}
	 * @param clazz
	 *            {@link #clazz}
	 * @throws NullPointerException
	 *             <code>factory</code> หรือ <code>clazz</code> เป็น null
	 * @see #Model(EntityManagerFactory, Class, String)
	 * @see Alias
	 */
	public Model(EntityManagerFactory factory, Class<E> clazz)
			throws NullPointerException {
		this(factory, clazz, null);
	}

	/**
	 * ทำหน้าที่ในการดักจับ {@link Throwable} ที่เกิดในกระบวนการทำงานของ
	 * {@link Model} และ {@link Factory} แต่ไม่ได้ถูก throw ออกไป
	 *
	 * @param thrown
	 *            {@link Throwable Exception} ที่เกิดขึ้น
	 * @see #listener
	 */
	protected void caught(Throwable thrown) {
		listener.launch(thrown);
	}

	/**
	 * ตรวจสอบว่าใน Keyword สำหรับการอ้าง Field ใน Entity Class มีตัวแปร Alias
	 * Name อยู่แล้วหรือไม่ หากไม่มี จะ Return ค่าที่เติม Alias Name
	 * ให้เลยโดยอัตโนมัติ
	 *
	 * @param keyword
	 *            Keyword ที่ต้องการตรวจสอบ
	 * @return ค่า Keyword ที่มี Alias Name เป็นส่วนประกอบอยู่แล้ว
	 */
	public CharSequence ialias(String keyword) {
		return factory.hasAlias(this, keyword) ? keyword
				: new StringBuilder(as).append('.').append(keyword);
	}

	/**
	 * เพิ่มข้อมูลลงฐานข้อมูล
	 *
	 * @param entities
	 *            ข้อมูลที่ต้องการเพิ่มลงฐาน
	 * @return true หากเพิ่มข้อมูลในฐานได้สำเร็จ
	 * @see Factory#transaction(Function)
	 * @see EntityManager#persist(Object)
	 */
	public boolean add(@SuppressWarnings("unchecked") E... entities) {
		return factory.add(this, Arrays.asList(entities));
	}

	/**
	 * เพิ่มข้อมูลลงฐานข้อมูล
	 *
	 * @param entities
	 *            ข้อมูลที่ต้องการเพิ่มลงฐาน
	 * @return true หากเพิ่มข้อมูลในฐานได้สำเร็จ
	 * @see Factory#transaction(Function)
	 * @see EntityManager#persist(Object)
	 */
	public boolean add(Iterable<E> entities) {
		return factory.add(this, entities);
	}

	/**
	 * แก้ไขข้อมูลในฐานข้อมูล
	 *
	 * @param entities
	 *            ข้อมูลที่ต้องการให้แก้ไขในฐาน
	 * @return true หากแก้ไขข้อมูลในฐานได้สำเร็จ
	 * @see Factory#transaction(Function)
	 * @see EntityManager#merge(Object)
	 */
	public boolean edit(@SuppressWarnings("unchecked") E... entities) {
		return factory.edit(this, Arrays.asList(entities));
	}

	/**
	 * แก้ไขข้อมูลในฐานข้อมูล
	 *
	 * @param entities
	 *            ข้อมูลที่ต้องการให้แก้ไขในฐาน
	 * @return true หากแก้ไขข้อมูลในฐานได้สำเร็จ
	 * @see Factory#transaction(Function)
	 * @see EntityManager#merge(Object)
	 */
	public boolean edit(Iterable<E> entities) {
		return factory.edit(this, entities);
	}

	/**
	 * แก้ไขข้อมูลในฐานข้อมูล
	 * โดยการกำหนดค่าและระบุเงื่อนไขของข้อมูลที่ต้องการแก้ไข
	 *
	 * @param values
	 *            ค่าที่ต้องแก้ไขให้กับข้อมูล
	 * @param criteria
	 *            เงื่อนใขในการระบุข้อมูลที่ต้องการแก้ไข
	 * @param params
	 *            Parameter ในคำสั่ง <code>values</code> และ
	 *            <code>criteria</code> (รวมกัน)
	 * @return จำนวนข้อมูลที่ถูกแก้ใขฝให้มีผลตาม<code>value</code>ที่ระบุ<br />
	 *         (หากไม่สามารถแก้ไขข้อมูลได้ จะ return -1)
	 * @see Factory#jpql(Function, CharSequence, Object...)
	 * @see Query#executeUpdate()
	 */
	public int edit(
			CharSequence values, CharSequence criteria, Object... params) {
		return factory.edit(this, values, criteria, params);
	}

	/**
	 * แก้ไขข้อมูลในฐานข้อมูล
	 * โดยการกำหนดค่าและระบุเงื่อนไขของข้อมูลที่ต้องการแก้ไข
	 *
	 * @param values
	 *            ค่าที่ต้องแก้ไขให้กับข้อมูล
	 * @param criteria
	 *            เงื่อนใขในการระบุข้อมูลที่ต้องการแก้ไข
	 * @param params
	 *            Parameter อื่นๆนอกเหนือจาก <code>values</code> และ
	 *            <code>criteria</code>
	 * @return จำนวนข้อมูลที่ถูกแก้ใขฝให้มีผลตาม<code>value</code>ที่ระบุ<br />
	 *         (หากไม่สามารถแก้ไขข้อมูลได้ จะ return -1)
	 * @see #edit(CharSequence, CharSequence, Object...)
	 * @see ValueBuilder
	 */
	public int edit(
			Pair.Series values, Factory.Criteria criteria, Object... params) {
		try {
			ValueBuilder builder = new ValueBuilder(values, criteria, params);
			return factory.edit(
					this, builder.values, builder.criteria, builder.params);
		} catch (Throwable e) {
			caught(e);
			return -1;
		}
	}

	/**
	 * ลบข้อมูลในฐานข้อมูล
	 *
	 * @param entities
	 *            ข้อมูลที่ต้องการลบ (Entity Object หรือ ID)
	 * @return true หากสามารถลบข้อมูลได้ทำเร็จ
	 * @see Factory#id(Class, Object) id(Class, Object)
	 * @see Factory#transaction(Function) transaction(Function)
	 * @see EntityManager#getReference(Class, Object)
	 * @see EntityManager#remove(Object)
	 */
	public boolean del(Object... entities) {
		return factory.del(this, Arrays.asList(entities));
	}

	/**
	 * ลบข้อมูลในฐานข้อมูล
	 *
	 * @param entities
	 *            ข้อมูลที่ต้องการลบ
	 * @return true หากสามารถลบข้อมูลได้ทำเร็จ
	 * @see Factory#id(Class, Object) id(Class, Object)
	 * @see Factory#transaction(Function) transaction(Function)
	 * @see EntityManager#getReference(Class, Object)
	 * @see EntityManager#remove(Object)
	 */
	public boolean del(Iterable<Object> entities) {
		return factory.del(this, entities);
	}

	/**
	 * ลบข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 *
	 * @param criteria
	 *            เงื่อนใขในการระบุข้อมูลที่ต้องการลบ
	 * @param params
	 *            Parameter ใน <code>criteria</code>
	 * @return จำนวนข้อมูลที่ถูกลบ <br />
	 *         (หากไม่สามารถลบข้อมูลได้ จะ return -1)
	 * @see Factory#transaction(Function) transaction(Function)
	 * @see Query#executeUpdate()
	 */
	public int del(CharSequence criteria, Object... params) {
		return factory.del(this, criteria, params);
	}

	/**
	 * ลบข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 *
	 * @param criteria
	 *            เงื่อนไขในการระบุข้อมูลที่ต้องการลบข้อมูล
	 * @param params
	 *            Parameter อื่นๆนอกเหนือจาก <code>criteria</code>
	 * @return จำนวนข้อมูลที่ถูกลบ <br />
	 *         (หากไม่สามารถลบข้อมูลได้ จะ return -1)
	 * @see #del(CharSequence, Object...)
	 * @see CriteriaBuilder
	 */
	public int del(Factory.Criteria criteria, Object... params) {
		if (criteria == null) return factory.del(this, (CharSequence) null);
		try {
			CriteriaBuilder builder = new CriteriaBuilder(criteria, params);
			return factory.del(this, builder.criteria, builder.params);
		} catch (Throwable e) {
			caught(e);
			return -1;
		}
	}

	/**
	 * ลบข้อมูลในฐานข้อมูลที่มีค่า ณ Field ที่กำหนด ตรงกับค่าที่ระบบุ
	 *
	 * @param field
	 *            Field ที่กำหนดว่าจะเปรียบเทียบ
	 * @param value
	 *            ค่าที่นำมาเปรียบเทียบกับค่าใน <code>field</code>
	 * @return จำนวนข้อมูลที่ถูกลบ <br />
	 *         (หากไม่สามารถลบข้อมูลได้ จะ return -1)
	 * @see #del(Model.Factory.Criteria, Object...)
	 * @see CriteriaBuilder
	 */
	public int del(String field, Object value) {
		return del(new Criteria(field, value));
	}

	/**
	 * ค้นหาข้อมูลในฐานข้อมูล ณ Id ที่ระบุ
	 *
	 * @param id
	 *            Id ของข้อมูลที่ต้องการค้นหา
	 * @return ข้อมูลที่มีค่าตรงกับ <code>id</code> ที่ระบุ
	 * @throws IllegalArgumentException
	 *             <code>id</code> ไม่ใช่ Object ของ Primary Key
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see Factory#id(Class, Object)
	 * @see Factory#manager(Function)
	 * @see EntityManager#find(Class, Object)
	 */
	public E find(Object id)
			throws IllegalArgumentException, UnsupportedOperationException {
		return factory.find(this, id);
	}

	/**
	 * ค้นหาข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 *
	 * @param criteria
	 *            เงื่อนไขในการระบุข้อมูลที่ต้องการค้นหา
	 * @param params
	 *            Parameter ใน <code>criteria</code>
	 * @return ข้อมูลในฐานข้อมูล ณ เงื่อนไขที่ระบุ
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
	 *             ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see Factory#jpql(Function, Class, CharSequence, Object...)
	 * @see Factory#build(Model, StringBuilder, Object...)
	 * @see TypedQuery#getSingleResult()
	 */
	public E find(CharSequence criteria, Object... params)
			throws IllegalArgumentException, UnsupportedOperationException {
		return factory.find(this, criteria, params);
	}

	/**
	 * ค้นหาข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 *
	 * @param criteria
	 *            เงื่อนไขในการระบุข้อมูลที่ต้องการค้นหา
	 * @param params
	 *            Parameter อื่นๆ นอกเหนือจาก Parameter ใน <code>criteria</code>
	 * @return ข้อมูลในฐานข้อมูล ณ เงื่อนไขที่ระบุ
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
	 *             ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see #find(CharSequence, Object...)
	 * @see CriteriaBuilder
	 */
	public E find(Factory.Criteria criteria, Object... params)
			throws IllegalArgumentException, UnsupportedOperationException {
		if (criteria == null) return factory.find(this, null, params);
		CriteriaBuilder builder = new CriteriaBuilder(criteria, params);
		return factory.find(this, builder.criteria, builder.params);
	}

	/**
	 * ค้นหาข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 *
	 * @param criteria
	 *            เงื่อนไขในการระบุข้อมูลที่ต้องการค้นหา
	 * @return ข้อมูลในฐานข้อมูล ณ เงื่อนไขที่ระบุ
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see #find(Model.Factory.Criteria, Object...)
	 * @see CriteriaBuilder
	 */
	public E find(Factory.Criteria criteria)
			throws IllegalArgumentException, UnsupportedOperationException {
		return find(criteria, (Object[]) null);
	}

	/**
	 * ค้นหาข้อมูลในฐานข้อมูลที่มีค่า ณ Field ที่กำหนด ตรงกับค่าที่ระบบุ
	 *
	 * @param field
	 *            Field ที่กำหนดว่าจะเปรียบเทียบ
	 * @param value
	 *            ค่าที่นำมาเปรียบเทียบกับค่าใน <code>field</code>
	 * @param params
	 *            Parameter อื่นๆ นอกเหนือจาก <code>field</code> และ
	 *            <code>value</code>
	 * @return ข้อมูลที่มีค่า ณ <code>field</code> ตรงกับ <code>value</code>
	 *         ที่ระบบุ
	 * @throws NullPointerException
	 *             <code>field</code> เป็น null หรือ ""
	 * @throws IllegalArgumentException
	 *             <code>field</code>, <code>value</code> หรือ
	 *             <code>params</code> ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see #find(Model.Factory.Criteria, Object...)
	 * @see Logic
	 */
	public E find(String field, Object value, Object... params)
			throws NullPointerException,
			IllegalArgumentException,
			UnsupportedOperationException {
		return find(new Logic(field, value), params);
	}

	/**
	 * ค้นหาข้อมูลในฐานข้อมูล ณ Id ที่ระบุ
	 *
	 * @param id
	 *            Id ของข้อมูลที่ต้องการค้นหา
	 * @return ข้อมูลที่มีค่าตรงกับ <code>id</code> ที่ระบุ
	 * @throws IllegalArgumentException
	 *             <code>id</code> ไม่ใช่ Object ของ Primary Key
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see #finds(CharSequence, Object...)
	 * @see Factory#id(Class, Object)
	 */
	public List<E> finds(Object... id)
			throws IllegalArgumentException, UnsupportedOperationException {
		return factory.finds(this, id);
	}

	/**
	 * ค้นหาข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 *
	 * @param criteria
	 *            เงื่อนไขในการระบุข้อมูลที่ต้องการค้นหา
	 * @param params
	 *            Parameter ใน <code>criteria</code>
	 * @return ข้อมูลในฐานข้อมูล ณ เงื่อนไขที่ระบุ
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
	 *             ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see Factory#jpql(Function, Class, CharSequence, Object...)
	 * @see Factory#build(Model, StringBuilder, Object...)
	 * @see TypedQuery#getResultList()
	 */
	public List<E> finds(CharSequence criteria, Object... params)
			throws IllegalArgumentException, UnsupportedOperationException {
		return factory.finds(this, criteria, params);
	}

	/**
	 * ค้นหาข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 *
	 * @param criteria
	 *            เงื่อนไขในการระบุข้อมูลที่ต้องการค้นหา
	 * @param params
	 *            Parameter อื่นๆ นอกเหนือจาก Parameter ใน <code>criteria</code>
	 * @return ข้อมูลในฐานข้อมูล ณ เงื่อนไขที่ระบุ
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
	 *             ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see #finds(CharSequence, Object...)
	 * @see CriteriaBuilder
	 */
	public List<E> finds(Factory.Criteria criteria, Object... params)
			throws IllegalArgumentException, UnsupportedOperationException {
		if (criteria == null) return factory.finds(this, null, params);
		CriteriaBuilder builder = new CriteriaBuilder(criteria, params);
		return factory.finds(this, builder.criteria, builder.params);
	}

	/**
	 * ค้นหาข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 *
	 * @param criteria
	 *            เงื่อนไขในการระบุข้อมูลที่ต้องการค้นหา
	 * @return ข้อมูลในฐานข้อมูล ณ เงื่อนไขที่ระบุ
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see #finds(Model.Factory.Criteria, Object...)
	 * @see CriteriaBuilder
	 */
	public List<E> finds(Factory.Criteria criteria)
			throws IllegalArgumentException, UnsupportedOperationException {
		return finds(criteria, (Object[]) null);
	}

	/**
	 * ค้นหาข้อมูลในฐานข้อมูลที่มีค่า ณ Field ที่กำหนด ตรงกับค่าที่ระบบุ
	 *
	 * @param field
	 *            Field ที่กำหนดว่าจะเปรียบเทียบ
	 * @param value
	 *            ค่าที่นำมาเปรียบเทียบกับค่าใน <code>field</code>
	 * @param params
	 *            Parameter อื่นๆ นอกเหนือจาก <code>field</code> และ
	 *            <code>value</code>
	 * @return ข้อมูลที่มีค่า ณ <code>field</code> ตรงกับ <code>value</code>
	 *         ที่ระบบุ
	 * @throws NullPointerException
	 *             <code>field</code> เป็น null หรือ ""
	 * @throws IllegalArgumentException
	 *             <code>field</code>, <code>value</code> หรือ
	 *             <code>params</code> ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see #finds(Model.Factory.Criteria, Object...)
	 * @see Logic
	 */
	public List<E> finds(String field, Object value, Object... params)
			throws NullPointerException,
			IllegalArgumentException,
			UnsupportedOperationException {
		return finds(new Logic(field, value), params);
	}

	/**
	 * ค้นหาข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 *
	 * @param selector
	 *            ตัวระบุข้อมูลจากการค้นหา
	 * @param criteria
	 *            เงื่อนไขในการระบุข้อมูลที่ต้องการค้นหา
	 * @param params
	 *            Parameter ใน <code>criteria</code>
	 * @return ข้อมูลในฐานข้อมูล ณ เงื่อนไขที่ระบุ
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
	 *             ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see Factory#jpql(Function, Class, CharSequence, Object...)
	 * @see Factory#build(Model, StringBuilder, Object...)
	 * @see TypedQuery#getResultList()
	 */
	public <R> List<R> finds(Factory.Selector<?, R> selector,
			CharSequence criteria,
			Object... params) {
		return factory.finds(this, selector, criteria, params);
	}

	/**
	 * ค้นหาข้อมูลในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 *
	 * @param selector
	 *            ตัวระบุข้อมูลจากการค้นหา
	 * @param criteria
	 *            เงื่อนไขในการระบุข้อมูลที่ต้องการค้นหา
	 * @param params
	 *            Parameter อื่นๆ นอกเหนือจาก Parameter ใน <code>criteria</code>
	 * @return ข้อมูลในฐานข้อมูล ณ เงื่อนไขที่ระบุ
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
	 *             ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see #finds(CharSequence, Object...)
	 * @see CriteriaBuilder
	 */
	public <R> List<R> finds(Factory.Selector<?, R> selector,
			Factory.Criteria criteria,
			Object... params) {
		if (criteria == null)
			return factory.finds(this, selector, null, params);
		CriteriaBuilder builder = new CriteriaBuilder(criteria, params);
		return factory.finds(this, selector, builder.criteria, builder.params);
	}

	/**
	 * ค้นหาข้อมูลในฐานข้อมูลที่มีค่า ณ Field ที่กำหนด ตรงกับค่าที่ระบบุ
	 *
	 * @param selector
	 *            ตัวระบุข้อมูลจากการค้นหา
	 * @param field
	 *            Field ที่กำหนดว่าจะเปรียบเทียบ
	 * @param value
	 *            ค่าที่นำมาเปรียบเทียบกับค่าใน <code>field</code>
	 * @param params
	 *            Parameter อื่นๆ นอกเหนือจาก <code>field</code> และ
	 *            <code>value</code>
	 * @return ข้อมูลที่มีค่า ณ <code>field</code> ตรงกับ <code>value</code>
	 *         ที่ระบบุ
	 * @throws NullPointerException
	 *             <code>field</code> เป็น null หรือ ""
	 * @throws IllegalArgumentException
	 *             <code>field</code>, <code>value</code> หรือ
	 *             <code>params</code> ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see #finds(Model.Factory.Criteria, Object...)
	 * @see Logic
	 */
	public <R> List<R> finds(Factory.Selector<?, R> selector,
			String field,
			Object value,
			Object... params) {
		return finds(selector, new Logic(field, value), params);
	}

	/**
	 * นับจำนวนในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 *
	 * @param criteria
	 *            เงื่อนไขในการระบุข้อมูลที่ต้องการนับ
	 * @param params
	 *            Parameter ใน <code>criteria</code>
	 * @return จำนวนข้อมูลที่นับได้
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
	 *             ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see Factory#jpql(Function, Class, CharSequence, Object...)
	 * @see Factory#build(Model, StringBuilder, Object...)
	 * @see TypedQuery#getSingleResult()
	 */
	public Long count(CharSequence criteria, Object... params)
			throws IllegalArgumentException, UnsupportedOperationException {
		return factory.find(this, (Factory.Selector.That<Long>) model -> {
			return "COUNT(" + model.as + ")";
		}, criteria, params);
	}

	/**
	 * นับจำนวนในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 *
	 * @param criteria
	 *            เงื่อนไขในการระบุข้อมูลที่ต้องการนับ
	 * @param params
	 *            Parameter อื่นๆ นอกเหนือจาก Parameter ใน <code>criteria</code>
	 * @return จำนวนข้อมูลที่นับได้
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
	 *             ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see #count(CharSequence, Object...)
	 * @see CriteriaBuilder
	 */
	public long count(Factory.Criteria criteria, Object... params)
			throws IllegalArgumentException, UnsupportedOperationException {
		if (criteria == null) return count((CharSequence) null);
		CriteriaBuilder builder = new CriteriaBuilder(criteria, params);
		return count(builder.criteria, builder.params);
	}

	/**
	 * นับจำนวนในฐานข้อมูลที่มีค่า ณ Field ที่กำหนด ตรงกับค่าที่ระบบุ
	 *
	 * @param field
	 *            Field ที่กำหนดว่าจะเปรียบเทียบ
	 * @param value
	 *            ค่าที่นำมาเปรียบเทียบกับค่าใน <code>field</code>
	 * @param params
	 *            Parameter อื่นๆ นอกเหนือจาก <code>field</code> และ
	 *            <code>value</code>
	 * @return จำนวนข้อมูลที่นับได้
	 * @throws NullPointerException
	 *             <code>field</code> เป็น null หรือ ""
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
	 *             ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see #count(Model.Factory.Criteria, Object...)
	 * @see Logic
	 */
	public long count(String field, Object value, Object... params)
			throws NullPointerException,
			IllegalArgumentException,
			UnsupportedOperationException {
		return count(new Logic(field, value), params);
	}

	/**
	 * นับจำนวนในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 * โดยแบ่งจำนวนข้อมูลที่นับได้ออกเป็นกลุ่มๆ
	 *
	 * @param fields
	 *            Field สำหรับแบ่งกลุ่มการนับข้อมูล
	 * @param criteria
	 *            เงื่อนไขในการระบุข้อมูลที่ต้องการนับ
	 * @param params
	 *            Parameter ใน <code>criteria</code>
	 * @return จำนวนข้อมูลที่นับได้โดยจำแนกออกเป็นกลุ่มๆ (ผลการนับ: key = null)
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
	 *             ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see Factory#jpql(Function, Class, CharSequence, Object...)
	 * @see Factory#build(Model, StringBuilder, Object...)
	 * @see TypedQuery#getResultList()
	 */
	public List<Map<String, Object>> counts(
			String fields, CharSequence criteria, Object... params)
			throws IllegalArgumentException,
			UnsupportedOperationException {
		Aggregate selector = agg(fields).with(null, "COUNT(" + as + ")");
		return factory.finds(this, selector, criteria, params);
	}

	/**
	 * นับจำนวนในฐานข้อมูลตามเงื่อนไขที่ระบุ
	 * โดยแบ่งจำนวนข้อมูลที่นับได้ออกเป็นกลุ่มๆ
	 *
	 * @param fields
	 *            Field สำหรับแบ่งกลุ่มการนับข้อมูล
	 * @param criteria
	 *            เงื่อนไขในการระบุข้อมูลที่ต้องการนับ
	 * @param params
	 *            Parameter อื่นๆ นอกเหนือจาก Parameter ใน <code>criteria</code>
	 * @return จำนวนข้อมูลที่นับได้
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
	 *             ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see #counts(String, CharSequence, Object...)
	 * @see CriteriaBuilder
	 */
	public List<Map<String, Object>> counts(
			String fields, Factory.Criteria criteria, Object... params)
			throws IllegalArgumentException, UnsupportedOperationException {
		if (criteria == null)
			return counts(fields, (CharSequence) null, params);
		CriteriaBuilder builder = new CriteriaBuilder(criteria, params);
		return counts(fields, builder.criteria, builder.params);
	}

	/**
	 * นับจำนวนในฐานข้อมูลที่มีค่า ณ Field ที่กำหนด ตรงกับค่าที่ระบบุ
	 * โดยแบ่งจำนวนข้อมูลที่นับได้ออกเป็นกลุ่มๆ
	 *
	 * @param fields
	 *            Field สำหรับแบ่งกลุ่มการนับข้อมูล
	 * @param field
	 *            Field ที่กำหนดว่าจะเปรียบเทียบ
	 * @param value
	 *            ค่าที่นำมาเปรียบเทียบกับค่าใน <code>field</code>
	 * @param params
	 *            Parameter อื่นๆ นอกเหนือจาก <code>field</code> และ
	 *            <code>value</code>
	 * @return จำนวนข้อมูลที่นับได้
	 * @throws NullPointerException
	 *             <code>field</code> เป็น null หรือ ""
	 * @throws IllegalArgumentException
	 *             คำสั่ง <code>criteria</code> หรือ <code>params</code>
	 *             ไม่ถูกต้อง
	 * @throws UnsupportedOperationException
	 *             ไม่สามารถเชื่อมต่อฐานข้อมูลได้
	 * @see #counts(String, Model.Factory.Criteria, Object...)
	 * @see Logic
	 */
	public List<Map<String, Object>> counts(
			String fields, String field, Object value, Object... params)
			throws NullPointerException,
			IllegalArgumentException,
			UnsupportedOperationException {
		return counts(fields, new Logic(field, value), params);
	}

	/**
	 * ล้าง {@link Cache} ใน {@link EntityManagerFactory}
	 *
	 * @param id
	 *            ID ของข้อมูลที่ต้องการล้าง {@link Cache} (<code>id</code> เป็น
	 *            null จะล้าง {@link Cache} ทั้ง <code>model</code>)
	 * @return true หากสามารถล้าง {@link Cache} ได้สำเร็จ
	 * @see Factory#factory(Function)
	 * @see Factory#id(Class, Object)
	 * @see Cache#evict(Class)
	 * @see Cache#evict(Class, Object)
	 */
	public boolean clear(Object... id) {
		return factory.clear(this, id);
	}
}