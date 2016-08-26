# jpa-model
Class Model สำหรับใช้ในการเข้าถึงหรือปฏิบัติต่อข้อมูลในตารางใดๆของระบบจัดการฐานข้อมูล ตาม Entity Class ผ่าน EntityManager ของ Persistence Framework 
 ตัวอย่างการใช้งาน
 1. สร้าง Model Object
 	EntityManagerFactory factory = Persistence.createEntityManagerFactory(String);
 	Model<SomeEntity> model = new Model<>(factory, SomeEntity.class);
 // หรือ
 	Model.Factory factory = new Model.Factory.Static(
 			Persistence.createEntityManagerFactory(String));
 	Model<SomeEntity> model = factory.create(SomeEntity.class);
 2. เพิ่มข้อมูล
 	SomeEntity entity = new SomeEntity();
 	entity.setSomeValue(...);
 	model.add(entity)
 3. ค้นหาข้อมูล
 	SomeEntity find = model.find(...);
 // หรือ
 	List<SomeEntity> finds = model.finds(...);
 4. ปรับปรุงข้อมูล
 	find.setSomeValue(...);
 	model.put(find);
  // หรือ
 	for(SomeEntity find : finds){
 		find.setSomeValue(...);
 	}
 	model.put(finds);
 5. ลบข้อมูล
 	model.del(find);
  // หรือ
 	model.del(finds);
